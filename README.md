# VESC Dash

An Android app that connects to a VESC motor controller over Bluetooth and gives you:

- **A customizable live dashboard.** You get several swipeable pages of widgets (number, gauge, bar, live graph). Each widget can show any metric, with its own size, scale and warning/danger colors.
- **Two appearances (Setup → Appearance).** Classic is the original look. Stark Varg is an unofficial, landscape-only lookalike of the Stark Varg phone app (not affiliated with Stark Future): neutral grays, a wide caps face and neon green, amber, red and cyan accents throughout. Switching applies immediately and locks the app to landscape. Navigation moves to a slim left rail with a red active marker, the Modes tab becomes a single power-mode editor with glowing tick-scale sliders, and Setup and the widget editor use two columns. The ride view opens by itself when you ride off (above 5 km/h) or from the RIDE button on the rail, and its home button returns to the app.
- **Drive modes you can switch between.** Up to 6 modes, one tap to switch from the dashboard, in a Stark Varg-style mode strip.
- **A full-screen ride view in landscape** laid out like the Stark Varg ride screen. It shows power mode, battery level and riding time across the top, a segmented battery arc, a torque/regen bar and big speed digits. Tap the ⚡ pill to change modes. When you're stopped, a home button takes you back to the app, and the ride view returns once you're moving above 5 km/h.
- **Four ride styles, light or dark.** Choose between the Classic arc gauge, a Minimal layout (mode, battery and riding time along the top, a huge speed readout, and one power/regen bar), Tiles (a rounded strip of mode, speed and battery tiles, a power/regen bar with the trip beneath it, and a clock / riding time / motor temperature line along the bottom), and Custom (your own layout, below). Each comes in Dark, Light or Auto (Setup → Ride view). Entering the ride view zooms in and runs a key-on sweep of the gauges.
- **A ride screen you design yourself.** The Custom style places dashboard widgets (number, gauge, bar, graph, any metric) anywhere on the landscape screen, with their text sized to fit, next to the mode pill and warning icons. To edit it, tap the pencil beside the home button when you're stopped, or use Setup → Ride view → Edit custom layout. Drag a widget to move it, pinch anywhere to resize the selected one or drag its corner handles, and use the toolbar to add, edit, duplicate, reorder or delete widgets. Edges snap to the screen and to each other, and Undo, Reset and Save are in the toolbar.
- **Alpha-mode launch sequence.** Switching into your most powerful mode (the one with the highest estimated peak power) while on the ride view plays a 2.6-second cinematic: a flash, a screen shake with a haptic hit, letterbox bars, ALPHA slamming in with shockwaves and sparks, the stats typing out, and an ALPHA MODE power meter filling up. Tap to skip.
- **Launch animation (optional).** Turn it on in Setup → Ride view and hard acceleration from a standstill (or a very hard pull at speed) brings speed streaks flying out from the centre, a glow in your mode's colour around the screen edges and a small push on the whole view, with a light haptic tick. It follows your throttle, fades out when you let off, and rests for a couple of seconds before the next one. Demo mode launches every 70 seconds so you can see it.
- **Heat warnings.** Separate controller and motor icons appear at 60 °C by default (you can change each one in Setup → Heat warnings) with the live temperature. They shift from yellow to red as the heat climbs (already orange-red a quarter of the way to the danger point), and pulse once they hit the danger point (85 °C for the controller, 100 °C for the motor).
- **Stable battery %.** The app learns the pack's internal resistance and adds back the voltage sag, so the reading doesn't drop when you accelerate. If you enter the pack capacity (Setup → Battery), it also counts the amp-hours used, which gives the steadiest reading.
- **Demo mode** (Setup → Controller) plays simulated ride data so you can try the UI without a VESC.
- **A basic power tuner for each mode.** Sliders set power %, regen braking %, top speed limit and power cap. It shows an estimated peak kW/hp and a power-vs-speed curve compared against 100%.

## Build & install

1. Install [Android Studio](https://developer.android.com/studio). It includes the JDK and Android SDK.
2. **File → Open…** and pick this `vesc-dash` folder. Let Gradle sync finish. It downloads Gradle and the dependencies the first time.
3. On your phone, turn on **Developer options → USB debugging**, then plug it in.
4. Press **Run ▶**. Android Studio builds the app and installs it on the phone.

Android 8.0 (API 26) or newer is required.

## First-time setup (in the app)

1. **Setup tab → Vehicle.** Enter motor poles, motor KV, gear ratio, wheel diameter and battery cells in series. Speed, distance, battery % and the power estimates all depend on these values.
2. **Setup tab → Base limits.** Copy these from VESC Tool's motor config:
   - motor current max
   - battery current max
   - battery regen max
   - max ERPM
   - max duty

   Drive modes only ever *scale down* from these values.
3. Tap the connection pill (top right) and pick your VESC. Close VESC Tool first, because a BLE module accepts only one connection at a time.

## How drive modes talk to the VESC

Selecting a mode sends `COMM_SET_MCCONF_TEMP` with `store = false`. That changes the limits live **without writing flash**, so power-cycling the controller restores your VESC Tool configuration. The mode is re-sent every time the app connects; you can turn that off in Setup.

| Tuner control | VESC parameter |
|---|---|
| Power % | `l_current_max_scale`, and battery current max × % |
| Regen % | `l_current_min_scale`, and battery regen max × % (**this is also your electric brake strength**) |
| Top speed | `l_max_erpm` / `l_min_erpm`, calculated from wheel, gearing and poles |
| Power cap | `l_watt_max` |

With **Dual controller (CAN)** turned on, the mode is forwarded to every controller on the CAN bus, and telemetry from the second controller is merged into the dashboard: currents and energy are added together, and temperatures show whichever controller is hottest.

The power curve is an **estimate** based on KV, pack voltage and your limits. It is meant for comparing modes, not a dyno reading.

## Project layout

```
app/src/main/java/com/vescdash/
  vesc/   packet framing, CRC16, COMM_* encode/decode   (pure Kotlin, unit-tested)
  ble/    BLE Nordic-UART transport (scan, connect, MTU, chunked writes)
  data/   settings/models, metrics, vehicle math, repository (polling, modes, auto-reconnect)
  ui/     Compose screens: dash/, modes/ (tuner + power curve), setup/, connect/
```

Run the protocol unit tests with **Gradle → app → Tasks → verification → test**, or with `./gradlew test` from the project folder.

## Credits

The ride view uses the [Michroma](https://github.com/googlefonts/Michroma-font) typeface, licensed under the SIL Open Font License 1.1 (see `licenses/Michroma-OFL.txt`). The Stark Varg appearance also uses [Outfit](https://github.com/Outfitio/Outfit-Fonts) and [Krona One](https://fonts.google.com/specimen/Krona+One), both under the SIL Open Font License 1.1 (`licenses/Outfit-OFL.txt`, `licenses/KronaOne-OFL.txt`). The ride view is inspired by the Stark Varg's dashboard; this project is not affiliated with Stark Future, and the Stark Varg appearance is an unofficial lookalike that uses no Stark logos, wordmarks or images.

## Regen when you let off the throttle

A mode's **Regen %** scales how hard the VESC brakes, but the VESC only brakes when its input asks it to. With a thumb/twist throttle on the ADC input, releasing the throttle normally means "0 A": the motor coasts and no regen happens. Pick the setup that matches your wiring.

### With a regen brake lever on ADC2 (control type "Current No Reverse Brake ADC2")

Use the LispBM script in [`vesc-scripts/coast-regen.lisp`](vesc-scripts/coast-regen.lisp). It runs on the VESC itself:

- **Throttle released, lever not pulled, faster than 1 mph:** regen at `coast-level` of your brake current, eased in over `ramp-time`. The active mode's Regen % scales it, the same as it scales the lever.
- **Touch the throttle, pull the lever, or drop below 1 mph:** control goes straight back to the normal ADC app, so the lever works exactly as before.
- **Fail-safe:** the script pauses the ADC app only 100 ms at a time. If it ever stops, throttle and lever control return within 0.1 s.

To install it:

1. In VESC Tool, make sure the wheel diameter, motor poles and gear ratio are set (the setup wizard does this). The 1 mph check uses them.
2. Open **VESC Dev Tools → LispBM**, paste the script and press **Upload**. It starts automatically on every power-up.
3. Adjust `coast-level` (0.0–1.0) and `min-speed` at the top of the script to taste, then upload again.
4. Test with the wheel off the ground: coast regen should kick in on release, the lever should still brake, and a touch of throttle should cancel the regen.

The script hasn't been tested on hardware yet. If VESC Tool's LispBM console shows an error, send it over.

### Throttle only (no brake lever)

1. **App Settings → General → App to Use:** `ADC`.
2. **App Settings → ADC → General → Control Type:** `Current No Reverse Brake Center`.
3. **App Settings → ADC → Mapping** (watch the live ADC1 voltage):
   - **Min Voltage:** the reading with the throttle released.
   - **Max Voltage:** the reading at full throttle.
   - **Center Voltage:** slightly above the released reading, about 5–10 % of the travel. Below this is regen, above it is power.
4. **App Settings → ADC → General → Ramp time neg:** about `0.3 s`, so regen eases in.
5. **Motor Settings → General → Current → Motor Current Min (Regen):** this is 100 % regen; each mode's Regen % scales down from it. Make `Battery Current Min (Regen)` match **Battery regen max** in the app.
6. Write the config and test with the wheel off the ground first.

With the throttle fully released you get full regen at the mode's Regen %. It fades out as you open the throttle toward the center point, then power takes over. Regen naturally fades as speed drops, and this control type can't drive the motor backwards.

## Safety

- Test new modes at low speed first.
- On vehicles where regen is the only brake (e-skateboards, some scooters), keep regen high. The slider cannot go below 10%.
- The app can only lower limits relative to the base values you enter. If those values are higher than your real VESC Tool config, the app will raise the limits to match them.
