# Landing page

Static site for GravityChunk. It describes the GPU pipeline, the setup presets, and how to build the desktop app. It does not run the simulation.

## Preview

From this directory:

```powershell
python -m http.server 8000
```

Open `http://127.0.0.1:8000/`.

## Vercel

The Vercel project `gravity-website` serves this folder. The portfolio links to `https://gravity-website-gabes-projects-3f71fd6a.vercel.app`.

`website/public/` is only for optional captures. `vercel.json` sets the output directory to this folder so Vercel publishes `index.html` instead of `public/`. The repository-root `vercel.json` does that when the project root is the repo. `website/vercel.json` does it when the project root directory is `website`.

Paths in the HTML are relative, so the same files work at the domain root and under a subpath such as GitHub Pages.

## GitHub Pages

`.github/workflows/pages.yml` publishes this folder from `main`. In the repository settings, set Pages → Build and deployment → Source to **GitHub Actions** once. The site is then `https://gkane1234.github.io/gravity/`.

## Optional captures

`scripts/encode-capture.ps1` can write mp4/webm files into `public/videos/`. The page embeds `videos/sim.mp4`, which is the web-sized recording kept next to `index.html`. Files in `public/videos/` stay gitignored.
