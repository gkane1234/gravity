# Website drop-in

Copy into your existing React site:

1. `GravityShowcase.jsx` → e.g. `src/pages/GravityShowcase.jsx` (or a projects route)
2. Encoded videos → your site's `public/videos/`
3. Add a route that renders `<GravityShowcase />`

Record clips in the desktop app with **F6**, then encode:

```powershell
.\scripts\encode-capture.ps1 -CaptureDir captures\rec_YYYYMMDD_HHMMSS -Name hero
```

Download CTA uses: https://github.com/gkane1234/gravity/releases/latest
