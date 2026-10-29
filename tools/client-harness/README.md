# Vanilla-client screenshot harness

A 26.3 client with only Fabric Loader and a tiny harness mod (no Fabric API, no Create), so the
server sees it like an unmodded player. It joins `localhost:25565`, accepts the server resource
pack, runs `run/harness_script.txt` (`wait N`, `cmd /...`, `shot NAME`, `quit`) and saves
screenshots to `run/screenshots/`.

```bash
cd tools/client-harness
cp -r ../../gradle ../../gradlew .
cp example_script.txt run/harness_script.txt   # after the first launch creates run/
./gradlew runClient --args="--width 1280 --height 720"
```

The test player must be op on the server (offline-mode UUID for "Tester": f3d28cb0-7225-3cb1-baeb-2dadd2be89ae).
On a headless Linux box run it under `xvfb-run -s "-screen 0 1280x720x24 +extension GLX"` with
`LIBGL_ALWAYS_SOFTWARE=1`; the `GlBackendMixin` drops the sRGB framebuffer request, which Xvfb cannot satisfy.
The first launch shows an onboarding screen: set `onboardAccessibility:false` in `run/options.txt`.
