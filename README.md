# VESC Dash

An Android app that connects to a VESC motor controller over Bluetooth and gives you:

- **A customizable live dashboard.** You get several swipeable pages of widgets (number, gauge, bar, live graph). Each widget can show any metric, with its own size, scale and warning/danger colors.
- **Drive modes you can switch between.** Up to 6 modes, one tap to switch from the dashboard, in a Stark Varg-style mode strip.
- **A full-screen ride view in landscape** laid out like the Stark Varg ride screen. It shows power mode, battery level and riding time across the top, a segmented battery arc, a torque/regen bar and big speed digits. Tap the ⚡ pill to change modes. When you're stopped, a home button takes you back to the app, and the ride view returns once you're moving above 5 km/h.
- **Three ride styles, light or dark.** Choose between the Classic arc gauge, a Minimal layout (mode, battery and riding time along the top, a huge speed readout, and one power/regen bar), and Tiles (a rounded strip of mode, speed and battery tiles, a power/regen bar with the trip beneath it, and a clock / riding time / motor temperature line along the bottom). Each comes in Dark, Light or Auto (Setup → Ride view). Entering the ride view zooms in and runs a key-on sweep of the gauges.
- **Alpha-mode launch sequence.** Switching into your most powerful mode (the one with the highest estimated peak power) while on the ride view plays a 2.6-second cinematic: a flash, a screen shake with a haptic hit, letterbox bars, ALPHA slamming in with shockwaves and sparks, the stats typing out, and an ALPHA MODE power meter filling up. Tap to skip.
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

The ride view uses the [Michroma](https://github.com/googlefonts/Michroma-font) typeface, licensed under the SIL Open Font License 1.1 (see `licenses/Michroma-OFL.txt`). The ride view is inspired by the Stark Varg's dashboard; this project is not affiliated with Stark Future.

## Safety

- Test new modes at low speed first.
- On vehicles where regen is the only brake (e-skateboards, some scooters), keep regen high. The slider cannot go below 10%.
- The app can only lower limits relative to the base values you enter. If those values are higher than your real VESC Tool config, the app will raise the limits to match them.
