{ config, stdenv, stdenvNoCC, callPackage,
  pkgs, mkFilter, androidenv, fetchurl, openjdk, nodejs, bash, gradle, zlib,
  status-go, localMavenRepoBuilder, nodeProjectName, projectNodePackage, developmentNodePackages, prod-build }:

with stdenv;

let
  inherit (callPackage ./android-env.nix { }) androidComposition licensedAndroidEnv;

  mavenAndNpmDeps = callPackage ./maven-and-npm-deps { inherit stdenvNoCC gradle bash zlib androidEnvShellHook localMavenRepoBuilder mkFilter nodeProjectName projectNodePackage developmentNodePackages status-go; };

  target-os = "android"; prod-build' = (prod-build { inherit projectNodePackage; });
  release-android = callPackage ./actions/release-android.nix { inherit target-os gradle androidEnvShellHook mavenAndNpmDeps mkFilter status-go zlib; prod-build = prod-build'; };

  androidEnvShellHook = ''
    export JAVA_HOME="${openjdk}"
    export ANDROID_HOME="${licensedAndroidEnv}"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export ANDROID_NDK_ROOT="${androidComposition.androidsdk}/libexec/android-sdk/ndk-bundle"
    export ANDROID_NDK_HOME="$ANDROID_NDK_ROOT"
    export ANDROID_NDK="$ANDROID_NDK_ROOT"
    export PATH="$ANDROID_HOME/bin:$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools:$PATH"
  '';

in
  {
    inherit androidComposition release-android;

    buildInputs = [ mavenAndNpmDeps.deps openjdk gradle ];
    shellHook =
      androidEnvShellHook + 
      (mavenAndNpmDeps.shellHook mavenAndNpmDeps) + ''
      $STATUS_REACT_HOME/scripts/generate-keystore.sh

      $STATUS_REACT_HOME/nix/mobile/reset-node_modules.sh "${mavenAndNpmDeps.deps}" && \
      $STATUS_REACT_HOME/nix/mobile/android/fix-node_modules-permissions.sh

      if [ $? -ne 0 ]; then
        # HACK: Allow CI to clean node_modules, will need to rethink this later
        [ -n "$JENKINS_URL" ] && chmod -R u+w "$STATUS_REACT_HOME/node_modules"
        exit 1
      fi
    '';
  }
