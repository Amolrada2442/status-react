{ config, stdenv, pkgs, callPackage, fetchurl, target-os,
  mkFilter, localMavenRepoBuilder, gradle, status-go, composeXcodeWrapper, nodejs, prod-build }:

with stdenv;

let
  platform = callPackage ../platform.nix { inherit target-os; };
  xcodewrapperArgs = {
    version = "10.1";
  };
  xcodeWrapper = composeXcodeWrapper xcodewrapperArgs;
  androidPlatform = callPackage ./android { inherit config pkgs mkFilter nodejs gradle status-go localMavenRepoBuilder nodeProjectName developmentNodePackages prod-build; projectNodePackage = projectNodePackage'; };
  iosPlatform = callPackage ./ios { inherit config pkgs mkFilter xcodeWrapper status-go nodeProjectName developmentNodePackages; projectNodePackage = projectNodePackage'; };
  selectedSources =
    lib.optional platform.targetAndroid androidPlatform ++
    lib.optional platform.targetIOS iosPlatform;

  developmentNodePackages = import ./node2nix/development { inherit pkgs nodejs; };
  projectNodePackage = import ./node2nix/StatusIm { inherit pkgs nodejs; };
  projectNodePackage' = projectNodePackage.package.override(oldAttrs: (realmOverrides oldAttrs) // {
    # Ensure that a package.json is present where node2nix's node-env.nix expects it, instead of the package.json.orig
    postPatch = ''
      outputPackage="$out/lib/node_modules/${nodeProjectName}/package.json"
      mkdir -p $(dirname $outputPackage)
      cp $src/package.json.orig $outputPackage
      chmod +w $outputPackage
      unset outputPackage
    '';
  });
  nodeProjectName = "StatusIm";
  realmOverrides = import ./realm-overrides { inherit stdenv target-os nodeProjectName fetchurl nodejs; };

in
  {
    inherit (androidPlatform) androidComposition release-android;
    inherit xcodewrapperArgs;

    buildInputs =
      status-go.buildInputs-android ++
      status-go.buildInputs-ios ++
      lib.catAttrs "buildInputs" selectedSources;
    shellHook = 
      status-go.shellHook-android +
      status-go.shellHook-ios +
      lib.concatStrings (lib.catAttrs "shellHook" selectedSources);

    prod-build = prod-build { projectNodePackage = projectNodePackage'; };
  }
