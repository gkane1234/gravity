# Landing page

Static site for GravityChunk. It describes the GPU pipeline, the setup presets, and how to build the desktop app. It does not run the simulation.

## Preview

From this directory:

```powershell
python -m http.server 8000
```

Open `http://127.0.0.1:8000/`.

## GitHub Pages

`.github/workflows/pages.yml` publishes this folder from `main`. In the repository settings, set Pages → Build and deployment → Source to **GitHub Actions** once. The site is then `https://gkane1234.github.io/gravity/`.

Paths in the HTML are relative, so the same files work at the domain root and under `/gravity/`.

## Optional captures

`scripts/encode-capture.ps1` can write mp4/webm files into `public/videos/`. The page does not reference them.
