/**
 * Drop-in GravityChunk showcase page for an existing React site.
 *
 * Copy this folder into your site (e.g. src/pages/GravityChunk or src/projects/gravity),
 * put encoded videos in your site's public/videos/, then route to <GravityShowcase />.
 *
 * Videos: run the sim, press F6 to record, then:
 *   .\scripts\encode-capture.ps1 -CaptureDir captures\rec_... -Name hero
 *
 * Download button points at GitHub Releases latest installer.
 */

const RELEASES_LATEST = "https://github.com/gkane1234/gravity/releases/latest";
const REPO_URL = "https://github.com/gkane1234/gravity";

const styles = {
  page: {
    maxWidth: "960px",
    margin: "0 auto",
    padding: "2rem 1.25rem 4rem",
    color: "inherit",
    fontFamily: "inherit",
  },
  heroVideo: {
    width: "100%",
    display: "block",
    background: "#000",
  },
  title: {
    fontSize: "clamp(1.75rem, 4vw, 2.5rem)",
    margin: "1.25rem 0 0.5rem",
    lineHeight: 1.15,
  },
  lead: {
    fontSize: "1.05rem",
    opacity: 0.85,
    maxWidth: "40rem",
    marginBottom: "1.25rem",
  },
  actions: {
    display: "flex",
    flexWrap: "wrap",
    gap: "0.75rem",
    marginBottom: "2.5rem",
  },
  button: {
    display: "inline-block",
    padding: "0.65rem 1.1rem",
    borderRadius: "6px",
    textDecoration: "none",
    fontWeight: 600,
    border: "1px solid currentColor",
  },
  primary: {
    background: "currentColor",
    color: "#111",
  },
  section: {
    marginTop: "2.5rem",
  },
  sectionTitle: {
    fontSize: "1.25rem",
    marginBottom: "0.75rem",
  },
  videoGrid: {
    display: "grid",
    gap: "1rem",
    gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))",
  },
  list: {
    paddingLeft: "1.2rem",
    lineHeight: 1.6,
  },
  note: {
    fontSize: "0.95rem",
    opacity: 0.8,
    marginTop: "0.75rem",
  },
};

function Video({ srcBase, caption }) {
  return (
    <figure style={{ margin: 0 }}>
      <video style={styles.heroVideo} controls playsInline preload="metadata">
        <source src={`${srcBase}.webm`} type="video/webm" />
        <source src={`${srcBase}.mp4`} type="video/mp4" />
        Your browser does not support embedded video.
      </video>
      {caption ? (
        <figcaption style={{ ...styles.note, marginTop: "0.4rem" }}>{caption}</figcaption>
      ) : null}
    </figure>
  );
}

/**
 * @param {{ heroSrc?: string, clips?: { src: string, caption?: string }[] }} props
 * heroSrc / clip src are paths under public without extension, e.g. "/videos/hero"
 */
export default function GravityShowcase({
  heroSrc = "/videos/hero",
  clips = [
    { src: "/videos/galaxy-merger", caption: "Galaxy merger" },
    { src: "/videos/dense-cluster", caption: "Dense cluster" },
  ],
} = {}) {
  return (
    <main style={styles.page}>
      <video
        style={styles.heroVideo}
        autoPlay
        muted
        loop
        playsInline
        poster={`${heroSrc}-poster.jpg`}
      >
        <source src={`${heroSrc}.webm`} type="video/webm" />
        <source src={`${heroSrc}.mp4`} type="video/mp4" />
      </video>

      <h1 style={styles.title}>GravityChunk</h1>
      <p style={styles.lead}>
        Real-time Barnes–Hut N-body simulation on the GPU — millions of bodies via
        OpenGL compute shaders, driven from Java / LWJGL.
      </p>

      <div style={styles.actions}>
        <a href={RELEASES_LATEST} style={{ ...styles.button, ...styles.primary }}>
          Download for Windows
        </a>
        <a href={REPO_URL} style={styles.button}>
          View source
        </a>
      </div>

      <section style={styles.section}>
        <h2 style={styles.sectionTitle}>More footage</h2>
        <div style={styles.videoGrid}>
          {clips.map((clip) => (
            <Video key={clip.src} srcBase={clip.src} caption={clip.caption} />
          ))}
        </div>
        <p style={styles.note}>
          Replace placeholder video paths after you encode captures with{" "}
          <code>scripts/encode-capture.ps1</code>.
        </p>
      </section>

      <section style={styles.section}>
        <h2 style={styles.sectionTitle}>How it works</h2>
        <ol style={styles.list}>
          <li>Morton encode bodies in the simulation AABB</li>
          <li>Parallel radix sort on Morton codes</li>
          <li>Build a binary spatial tree (Karras)</li>
          <li>Approximate forces with an acceptance criterion θ</li>
          <li>Merge / collide / handle out-of-bounds on the GPU</li>
        </ol>
      </section>

      <section style={styles.section}>
        <h2 style={styles.sectionTitle}>System requirements</h2>
        <ul style={styles.list}>
          <li>Windows 10/11 (64-bit)</li>
          <li>NVIDIA GPU with recent drivers (OpenGL 4.3+ compute)</li>
          <li>CUDA Toolkit is not required</li>
        </ul>
        <p style={styles.note}>
          The live simulation does not run in the browser. This page shows recorded
          output; download the Windows installer to run it locally.
        </p>
      </section>
    </main>
  );
}
