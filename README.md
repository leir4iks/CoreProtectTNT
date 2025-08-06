
[![Download](https://img.shields.io/badge/Download-Latest-007BFF?style=for-the-badge&logo=github)](https://github.com/leir4iks/CoreProtectTNT/releases/latest)

<div align="center">
<img src="https://raw.githubusercontent.com/leir4iks/CoreProtectTNT-updated/master/cpt-banner.webp" alt="CoreProtectTNT Banner">
</div>

<div align="center">

</div>

A CoreProtect addon allow you log the TNT (or TNTMinecart), Creeper, Fire, Ghast, Bed, Wind charge, Mace and even ItemFrame breaks!

### Added:
An auto-updater has been added. The plugin automatically checks for new versions and can download them for you. For full control, these options are available in the config:
```java
update-checker:
  enabled: true
  auto-download: true
  ```
A debug mode is also available:
```java
debug: false
```
New commands `/cptnt update` and `/cptnt version` have been added. Using them requires the permissions `coreprotecttnt.version` and `coreprotecttnt.update`.

### Improved:
Fire Tracking - Implemented a new 'fire-zone' system, ensuring almost 100% logging accuracy for damage from spreading fire.
TNT & Projectile Tracking - Significantly increased tracking precision to correctly identify the culprit even in the most complex scenarios.
Performance - The code has been carefully optimized to reduce server load during explosions and other resource-intensive events.

### Fixed:
1.21 Logic - Corrected the logging for Mace and Wind Charge attacks to ensure they are recorded clearly and accurately.

### BStats:

[![bStats Graph Data](https://bstats.org/signatures/bukkit/coreprotecttnt.svg)](https://bstats.org/plugin/bukkit/CoreProtectTNT/26755)