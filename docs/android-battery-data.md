# Android battery data is messier than it looks

Android makes battery percentage easy to read. Battery health, full-charge capacity, and cycle count are a different story.

While building [abCheck](https://github.com/abanana84/abCheck), formerly ABattery, whose battery diagnostics work without root or a network connection, I found that a reliable implementation needs a hierarchy of signals and an honest fallback strategy. A single API call is not enough across Pixels, Samsung devices, Xiaomi devices, older Android versions, and vendor kernels.

## 1. Start with the public Android signals

The sticky `ACTION_BATTERY_CHANGED` broadcast provides the most portable values:

- charge level and scale
- charging status and plug type
- temperature and voltage
- battery technology
- the platform health status

`BatteryManager` adds properties such as `BATTERY_PROPERTY_CURRENT_NOW` and `BATTERY_PROPERTY_CHARGE_COUNTER`. These are useful, but support and units still need validation. Some devices return a sentinel, zero, or no meaningful value.

abCheck therefore treats unavailable data as unavailable. It shows `N/A` instead of turning a missing signal into a confident-looking number.

## 2. Cycle count depends on Android and the manufacturer

On devices that expose it, the best source is `BatteryManager.EXTRA_CYCLE_COUNT`. It is a reported value, not an estimate.

Older devices and vendor builds may expose the same concept elsewhere. abCheck checks, in order:

1. the Android battery broadcast;
2. recognizable cycle fields from the system battery service;
3. readable power-supply files such as `cycle_count` under `/sys/class/power_supply`;
4. a local estimate based on accumulated charge, clearly labeled as estimated.

The final fallback does not reconstruct the battery's lifetime history. It can only accumulate information while the app is installed and the device exposes a usable charge counter. That limitation matters, so the UI marks the result explicitly.

## 3. Capacity has several meanings

"Battery capacity" can refer to at least three different values:

- **Design capacity:** the nominal capacity when the battery was designed.
- **Full-charge capacity:** the amount the battery currently reports it can hold at full charge.
- **Current charge:** the charge stored at this moment.

These values should not be mixed. A current charge counter at 50% is not a full-charge capacity unless it is normalized, and even then it is an inference rather than a laboratory measurement.

abCheck searches readable OEM power-supply nodes for design and full-charge values, checks system service output, and uses the device power profile as a nominal-capacity fallback. Values outside a plausible phone/tablet range are rejected.

## 4. Units require defensive handling

Depending on the signal, Android and Linux power-supply values may be expressed in microamps, microamp-hours, microvolts, milliamps, milliamp-hours, or millivolts.

Before displaying a value, an app should:

- know the expected unit for that source;
- reject documented sentinel values;
- apply plausible bounds;
- avoid silently converting an unknown unit;
- preserve whether the value was reported, calculated, or estimated.

Charging power is a good example. abCheck calculates it from current and voltage only when both inputs are present:

```text
power (W) = abs(current in A × voltage in V)
```

It is a live approximation, not a measurement of wall-adapter efficiency.

## 5. OEM compatibility is part of the product

Android battery diagnostics are not finished when they work on one emulator. Vendor kernels rename files, hide nodes, omit properties, and report different subsets of data.

The useful engineering loop is:

1. prefer stable public APIs;
2. add narrow, readable fallbacks;
3. validate ranges and units;
4. keep provenance in the domain model;
5. test on real devices from multiple manufacturers;
6. show `N/A` when evidence is insufficient.

That last step is important. Honest missing data creates more trust than an invented battery-health percentage.

## Help build the compatibility matrix

abCheck is available on [Google Play](https://play.google.com/store/apps/details?id=com.abanana.abattery), and its source is licensed under Apache-2.0.

If you have an Android device, you can help by adding a verified result to the [device compatibility matrix](https://github.com/abanana84/abCheck/issues/1). Include only the manufacturer, model, Android version, and which fields are available. Do not post serial numbers or account information.

Source: [github.com/abanana84/abattery](https://github.com/abanana84/abCheck)
