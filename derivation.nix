{ system ? builtins.currentSystem
, config ? { android_sdk.accept_license = true; }, overlays ? []
, pkgs ? (import <nixpkgs> { inherit system config overlays; })
, target-os }:

let
  platform = pkgs.callPackage ./nix/platform.nix { inherit target-os; };
  # TODO: Try to use stdenv for iOS. The problem is with building iOS as the build is trying to pass parameters to Apple's ld that are meant for GNU's ld (e.g. -dynamiclib)
  stdenv' = pkgs.stdenvNoCC;
  gradle = pkgs.gradle_4_10;
  go = pkgs.go_1_11;
  buildGoPackage = pkgs.buildGoPackage.override { inherit go; };
  statusDesktop = pkgs.callPackage ./nix/desktop { inherit target-os status-go pkgs go; inherit (pkgs) darwin; stdenv = stdenv'; nodejs = nodejs'; };
  statusMobile = pkgs.callPackage ./nix/mobile { inherit target-os config pkgs status-go gradle localMavenRepoBuilder mkFilter prod-build; inherit (pkgs.xcodeenv) composeXcodeWrapper; stdenv = stdenv'; nodejs = nodejs'; };
  status-go = pkgs.callPackage ./nix/status-go { inherit target-os go buildGoPackage; inherit (pkgs.xcodeenv) composeXcodeWrapper; inherit (statusMobile) xcodewrapperArgs; androidPkgs = statusMobile.androidComposition; };
  mkFilter = import ./nix/mkFilter.nix { inherit (stdenv') lib; };
  localMavenRepoBuilder = pkgs.callPackage ./nix/tools/maven/maven-repo-builder.nix { inherit (pkgs) stdenvNoCC; };
  prod-build = pkgs.callPackage ./nix/actions/prod-build.nix { inherit pkgs target-os localMavenRepoBuilder mkFilter; stdenv = stdenv'; };
  nodejs' = pkgs.nodejs-10_x;
  yarn' = pkgs.yarn.override { nodejs = nodejs'; };
  nodePkgBuildInputs = [
    nodejs'
    pkgs.nodePackages_10_x.react-native-cli
    pkgs.python27 # for e.g. gyp
    yarn'
  ];
  selectedSources =
    stdenv'.lib.optional platform.targetDesktop statusDesktop ++
    stdenv'.lib.optional platform.targetMobile statusMobile;

in {
  prod-build-android = statusMobile.prod-build;
  inherit (statusMobile) release-android;

  shell = with stdenv'; mkDerivation rec {
    name = "status-react-build-env";

    buildInputs = with pkgs;
      nodePkgBuildInputs
      ++ lib.optional isDarwin cocoapods
      ++ lib.optional (isDarwin && !platform.targetIOS) clang
      ++ lib.optional (!isDarwin) gcc7
      ++ lib.catAttrs "buildInputs" selectedSources;
    shellHook = lib.concatStrings (lib.catAttrs "shellHook" selectedSources);
  };
}
