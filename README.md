# Biscuit

Biscuit is a rudimentary bike computer, supporting ANT+ speed and cadence sensors as well as
GPS track recording

It started life as a fork of https://github.com/starryalley/CSC_BLE_Bridge/

## TO DO

tl;dr All the things

- some way to retry ANT scanning if it times out
- prettify the UI
- downloaded recorded tracks, ideally as GPX
- app icon
- notification icon

## Note to nixpkgs users

NIXPKGS_ALLOW_UNFREE=1  nix-shell -p firefox -p android-studio --run "android-studio ."
