import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Tuple

from skyfield.api import load, load_file, Loader


GravitationalConstant = 6.6743e-20 #km^3/s^2/kg


DATA_DIR = Path(__file__).parent
BSP_DIR = DATA_DIR / "planet_data_bsp"
MASS_TABLE_PATH = DATA_DIR / "naif_gm.json"
DEFAULT_OUTPUT_PATH = DATA_DIR / "planetary_state_vectors.json"

# Create loader that caches files in planet_data_bsp directory
BSP_DIR.mkdir(parents=True, exist_ok=True)
loader = Loader(str(BSP_DIR))
DEFAULT_KERNEL_NAMES = [
    "de440s.bsp",
    "de440.bsp",
    "de441.bsp",
    "de421.bsp",
    "jup347.bsp",
    "jup365.bsp",
    "mar099.bsp",
    "nep105.bsp",
    "plu060.bsp",
    "sat457.bsp",
    "ura111xl-799.bsp",
    "ura184.bsp"

]

Bodies_to_include = ["MOON", "EARTH", "SUN"]
SUN_NAIF_ID = 10


@dataclass
class BodyRecord:
    kernel_name: str
    kernel: object
    name: str
    center_id: int
    aliases: List[str]


def _ensure_text(value) -> str:
    if value is None:
        return ""
    if isinstance(value, bytes):
        return value.decode("utf-8")
    return str(value)


def _format_alias(alias: str) -> str:
    text = _ensure_text(alias).strip()
    if not text:
        return ""
    text = text.replace("_", " ")
    text = " ".join(text.split())
    if len(text) > 4 and text.upper() == text:
        return text.title()
    return text


def choose_canonical_name(target_id: int, aliases: Iterable[str], fallback: str | None) -> str:
    for alias in aliases or []:
        formatted = _format_alias(alias)
        if formatted:
            return formatted
    if fallback:
        formatted = _format_alias(fallback)
        if formatted:
            return formatted
    return f"NAIF {target_id}"


def prefer_candidate(candidate: BodyRecord, existing: BodyRecord) -> bool:
    if candidate.center_id == existing.center_id:
        return candidate.kernel_name < existing.kernel_name
    if candidate.center_id == SUN_NAIF_ID and existing.center_id != SUN_NAIF_ID:
        return True
    if existing.center_id == SUN_NAIF_ID and candidate.center_id != SUN_NAIF_ID:
        return False
    return candidate.kernel_name < existing.kernel_name


def load_mass_table() -> Dict[int, float]:

    try:
        raw = json.loads(MASS_TABLE_PATH.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        print(f"Warning: failed to parse {MASS_TABLE_PATH}: {exc}", file=sys.stderr)
        return {}

    masses: Dict[int, float] = {}
    for key, value in raw.items():
        try:
            naif_id = int(key)
        except (ValueError, TypeError):
            continue
        mass_value = None
        if isinstance(value, (int, float)):
            mass_value = value
        if mass_value is None:
            continue
        try:
            masses[naif_id] = float(mass_value) / GravitationalConstant
        except (TypeError, ValueError):
            continue
    return masses


def discover_kernel_paths() -> List[Path]:
    paths = {path.resolve() for path in BSP_DIR.glob("*.bsp")}
    return sorted(paths)


def load_kernels() -> Dict[str, object]:
    kernels: Dict[str, object] = {}

    for name in DEFAULT_KERNEL_NAMES:
        try:
            kernels[name] = loader(name)
        except Exception as e:
            try:
                kernels[name] = loader("https://ssd.jpl.nasa.gov/ftp/eph/satellites/bsp/" + name)
            except Exception as e:
                print(e)
                print(f"Error loading kernel {name}")
                continue

    for path in discover_kernel_paths():
        name = path.name
        if name in kernels:
            continue
        try:
            kernels[name] = load_file(str(path))
        except Exception:
            continue

    return kernels


def gather_bodies(kernels: Dict[str, object]) -> Tuple[Dict[int, BodyRecord], Dict[str, int]]:

    objects: Dict[int, BodyRecord] = {}
    alias_map: Dict[str, int] = {}

    objects[0] = BodyRecord(
        kernel_name="",
        kernel=None,
        name="Solar System Barycenter",
        center_id=0,
        aliases=["SOLAR_SYSTEM_BARYCENTER", "SOLAR SYSTEM BARYCENTER"],
    )

    for kernel_name, kernel in kernels.items():
        try:
            names_map = kernel.names()
        except AttributeError:
            continue

        def register_alias(alias: str | bytes, target_id: int) -> None:
            alias_text = _ensure_text(alias)
            if not alias_text:
                return
            upper = alias_text.upper()
            alias_map.setdefault(upper, target_id)
            alias_map.setdefault(upper.replace(" ", ""), target_id)

        for target_id, aliases in names_map.items():
            for alias in aliases:
                register_alias(alias, target_id)
                register_alias(_ensure_text(alias).replace("_", " "), target_id)

        for segment in getattr(kernel, "segments", []):
            target_id = getattr(segment, "target", None)
            center_id = getattr(segment, "center", None)
            if target_id in (None, 0) or target_id == center_id:
                continue

            alias_list = names_map.get(target_id, []) if hasattr(names_map, "get") else []
            canonical_name = choose_canonical_name(target_id, alias_list, getattr(segment, "target_name", None))

            alias_map.setdefault(str(target_id), target_id)
            alias_map.setdefault(canonical_name.upper(), target_id)

            candidate = BodyRecord(
                kernel_name=kernel_name,
                kernel=kernel,
                name=canonical_name,
                center_id=center_id if center_id is not None else 0,
                aliases=[_ensure_text(alias) for alias in alias_list],
            )
            existing = objects.get(target_id)
            if existing is None or prefer_candidate(candidate, existing):
                objects[target_id] = candidate

    return objects, alias_map


def resolve_reference(value: str, alias_map: Dict[str, int], objects: Dict[int, BodyRecord]) -> int:
    candidate = value.strip()
    if not candidate:
        raise SystemExit("Reference object cannot be empty.")

    lookup_key = candidate.upper()
    if lookup_key in alias_map and alias_map[lookup_key] in objects:
        return alias_map[lookup_key]

    compact_key = lookup_key.replace(" ", "")
    if compact_key in alias_map and alias_map[compact_key] in objects:
        return alias_map[compact_key]

    try:
        numeric_id = int(candidate, 0)
    except ValueError:
        raise SystemExit(f"Reference object '{value}' not found among loaded kernels.")

    if numeric_id not in objects:
        raise SystemExit(f"Reference object '{value}' not found among loaded kernels.")
    return numeric_id


def infer_center_name(record: BodyRecord) -> str:
    try:
        names_map = record.kernel.names()
    except AttributeError:
        names_map = {}
    aliases = names_map.get(record.center_id, []) if hasattr(names_map, "get") else []
    return choose_canonical_name(record.center_id, aliases, None)


def build_state_vectors(
    objects: Dict[int, BodyRecord],
    mass_table: Dict[int, float],
    reference_id: int,
    ) -> Tuple[List[Dict[str, object]], List[Tuple[int, str]], List[str], str]:
    timescale = loader.timescale()
    time = timescale.now()

    reference_record = objects[reference_id]
    reference_segment = reference_record.kernel[reference_id]
    reference_frame = reference_segment.at(time)

    missing_masses: List[Tuple[int, str]] = []
    used_kernels: set[str] = set()
    bodies: List[Dict[str, object]] = []

    sorted_objects = sorted(objects.items(), key=lambda item: (item[1].name.lower(), item[0]))
    for target_id, record in sorted_objects:

        if target_id == 0:
            continue

        print(target_id)
        if Bodies_to_include and record.name.upper() not in Bodies_to_include:
            continue

        used_kernels.add(record.kernel_name)
        segment = record.kernel[target_id]


        if target_id == reference_id:
            position = [0.0, 0.0, 0.0]
            velocity = [0.0, 0.0, 0.0]
        else:
            target_barycenter = objects[segment.center]
            if target_barycenter.kernel is None:
                target_barycenter_frame = reference_frame
            else:
                target_barycenter_segment = target_barycenter.kernel[segment.center]
                target_barycenter_frame = target_barycenter_segment.at(time)
            target_frame = segment.at(time)

            position = [1000*float(a + b) for a, b in zip(target_frame.position.km, target_barycenter_frame.position.km)]
            velocity = [1000*float(a + b) for a, b in zip(target_frame.velocity.km_per_s, target_barycenter_frame.velocity.km_per_s)]

        mass = mass_table.get(target_id)
        if mass is None:
            missing_masses.append((target_id, record.name))
        else:
            print(record.name.upper())
            if record.name.upper().find("BARYCENTER") != -1:
                print("skipping barycenter")
                continue

            bodies.append(
                {
                    "naif_id": target_id,
                    "name": record.name,
                    "center_naif_id": record.center_id,
                    "center_name": infer_center_name(record),
                    "mass": mass,
                    "position": position,
                    "velocity": velocity,
                    "kernel": record.kernel_name,
                    "aliases": record.aliases,
                }
            )

    return bodies, missing_masses, sorted(used_kernels), time.utc_iso()


def main(argv: List[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Export planetary state vectors relative to a reference body.")
    parser.add_argument(
        "--relative-to",
        dest="relative_to",
        default="SUN",
        help="Name or NAIF ID of the reference object (default: SUN)",
    )
    parser.add_argument(
        "--output",
        dest="output",
        default=str(DEFAULT_OUTPUT_PATH),
        help=f"Path to the JSON file to write (default: {DEFAULT_OUTPUT_PATH.name})",
    )
    args = parser.parse_args(argv)

    kernels = load_kernels()
    if not kernels:
        raise SystemExit("No SPICE kernels were loaded. Place .bsp files alongside this script or in the Skyfield data directory.")

    mass_table = load_mass_table()
    objects, alias_map = gather_bodies(kernels)

    if not objects:
        raise SystemExit("No bodies were discovered in the loaded kernels.")

    # Ensure direct numeric lookups are available.
    for naif_id in objects:
        alias_map.setdefault(str(naif_id), naif_id)

    reference_id = resolve_reference(args.relative_to, alias_map, objects)
    bodies, missing_masses, used_kernels, iso_timestamp = build_state_vectors(objects, mass_table, reference_id)

    reference_record = objects[reference_id]
    output_path = Path(args.output).resolve()
    output_data = {
        "relative_to": {
            "name": reference_record.name,
            "naif_id": reference_id,
        },
        "epoch": iso_timestamp,
        "units": {
            "position": "meters",
            "velocity": "meters per second",
            "mass": "kilograms",
        },
        "source_kernels": used_kernels,
        "body_count": len(bodies),
        "bodies": bodies,
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(output_data, indent=2), encoding="utf-8")

    relative_display = output_path.relative_to(DATA_DIR) if output_path.is_relative_to(DATA_DIR) else output_path
    print(f"Wrote {len(bodies)} bodies relative to {reference_record.name} into {relative_display}.")
    if missing_masses:
        missing_preview = ", ".join(f"{name} ({naif_id})" for naif_id, name in missing_masses[:10])
        print(
            f"Mass values missing for {len(missing_masses)} bodies: {missing_preview}...",
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())

