{ stdenv, stdenvNoCC, lib, callPackage,
  gradle, bash, file, status-go, zlib,
  nodeProjectName, projectNodePackage, developmentNodePackages, androidEnvShellHook, localMavenRepoBuilder, mkFilter }:

let
  mavenLocalRepos = import ./maven/maven-repo.nix { inherit stdenvNoCC localMavenRepoBuilder; };

  jsc-filename = "jsc-android-236355.1.1";
  react-native-deps = callPackage ./maven/reactnative-android-native-deps.nix { inherit stdenvNoCC jsc-filename; };

  src =
    let path = ./../../../..; # Import the root /android and /mobile_files folders clean of any build artifacts
    in builtins.path { # We use builtins.path so that we can name the resulting derivation, otherwise the name would be taken from the checkout directory, which is outside of our control
      inherit path;
      name = "status-react";
      filter =
        mkFilter {
          dirRootsToInclude = [
            "android" "mobile_files" "packager" "resources"
            "translations" "status-modules"
          ];
          dirsToExclude = [ ".git" ".svn" "CVS" ".hg" ".gradle" "build" "intermediates" "libs" "obj" ];
          filesToInclude = [ ".babelrc" ];
          root = path;
        };
    };

  # fake build to pre-download deps into fixed-output derivation
  deps = stdenv.mkDerivation {
    name = "gradle-install-android-archives-and-patched-npm-modules";
    inherit src;
    nativeBuildInputs = builtins.attrValues developmentNodePackages;
    buildInputs = [ gradle bash file zlib ] ++ status-go.buildInputs-android;
    unpackPhase = ''
      runHook preUnpack

      cp -a $src/. .
      chmod u+w .

      # Copy fresh RN maven dependencies and make them writable, otherwise Gradle copy fails
      cp -a ${react-native-deps}/deps ./deps

      # Copy fresh node_modules
      rm -rf ./node_modules
      mkdir -p ./node_modules
      cp -a ${projectNodePackage}/lib/node_modules/${nodeProjectName}/node_modules .

      # Adjust permissions
      chmod -R u+w .

      cp -R status-modules/ node_modules/status-modules/
      cp -R translations/ node_modules/status-modules/translations/

      # Set up symlinks to mobile enviroment in project root 
      ln -sf ./mobile_files/package.json.orig package.json
      ln -sf ./mobile_files/metro.config.js

      # Create a dummy VERSION, since we don't want this expression to be invalidated just because the version changed
      echo '0.0.1' > VERSION

      runHook postUnpack
    '';
    patchPhase = ''
      runHook prePatch

      patchShebangs .

      function patchMavenSource() {
        local targetGradleFile="$1"
        local source="$2"
        local deriv="$3"
        grep "$source" $targetGradleFile > /dev/null && substituteInPlace $targetGradleFile --replace "$source" "maven { url \"$deriv\" }" || echo -n ""
      }

      function patchMavenSources() {
        local targetGradleFile="$1"
        local deriv="$2"
        patchMavenSource $targetGradleFile 'mavenLocal()' "$deriv"
        patchMavenSource $targetGradleFile 'mavenCentral()' "$deriv"
        patchMavenSource $targetGradleFile 'google()' "$deriv"
        patchMavenSource $targetGradleFile 'jcenter()' "$deriv"
        grep 'https://maven.google.com' $targetGradleFile > /dev/null && substituteInPlace $targetGradleFile --replace 'https://maven.google.com' "$deriv" || echo -n ""
        grep '$rootDir/../node_modules/react-native/android' $1 > /dev/null && substituteInPlace $1 --replace '$rootDir/../node_modules/react-native/android' '${mavenLocalRepos.react-native-android}' || echo -n ""
      }

      # Patch maven and google central repositories with our own local directories. This prevents the builder from downloading Maven artifacts
      patchMavenSources 'node_modules/react-native/build.gradle' '${mavenLocalRepos."StatusIm"}'
      ${lib.concatStrings (lib.mapAttrsToList (projectName: deriv:
        let targetGradleFile = if projectName == nodeProjectName then "android/build.gradle" else "node_modules/${projectName}/android/build.gradle";
        in ''
          patchMavenSources '${targetGradleFile}' '${deriv}'
        '') (lib.filterAttrs (name: value: name != "react-native-android") mavenLocalRepos))}

      # Patch prepareJSC so that it doesn't try to download from registry
      substituteInPlace node_modules/react-native/ReactAndroid/build.gradle \
        --replace 'prepareJSC(dependsOn: downloadJSC)' 'prepareJSC(dependsOn: createNativeDepsDirectories)' \
        --replace 'def jscTar = tarTree(downloadJSC.dest)' 'def jscTar = tarTree(new File("../../../deps/${jsc-filename}.tar.gz"))'

      # We don't want to include the scripts directory, as this would invalidate the Nix cache build every time an unrelated script changed. In any case, the version shouldn't matter for this build.
      substituteInPlace android/app/build.gradle \
        --replace 'versionCode getVersionCode()' 'versionCode 9999'

      # HACK: Run what would get executed in the `prepare` script (though index.js.flow will be missing)
      # Ideally we'd invoke `npm run prepare` instead, but that requires quite a few additional dependencies
      (cd ./node_modules/react-native-firebase && \
       chmod u+w -R . && \
       mkdir ./dist && \
       genversion ./src/version.js && \
       cp -R ./src/* ./dist && \
       chmod u-w -R .) || exit

      runHook postPatch
    '';
    buildPhase = 
      androidEnvShellHook +
      status-go.shellHook-android + ''
      export REACT_NATIVE_DEPENDENCIES="$(pwd)/deps" # Use local writable deps, otherwise (for some unknown reason) gradle will fail copying directly from the nix store
      ( cd android
        LD_LIBRARY_PATH=$LD_LIBRARY_PATH:${lib.makeLibraryPath [ zlib ]} \
          gradle --offline --no-build-cache --no-daemon react-native-android:installArchives
      )
    '';
    installPhase = ''
      rm -rf $out
      mkdir -p $out
      # TODO: maybe node_modules/react-native/ReactAndroid/build/{intermediates,tmp,generated} can be discarded?
      cp -R android/ node_modules/ $out

      # Patch prepareJSC so that it doesn't subsequently try to build NDK libs
      substituteInPlace $out/node_modules/react-native/ReactAndroid/build.gradle \
        --replace 'packageReactNdkLibs(dependsOn: buildReactNdkLib, ' 'packageReactNdkLibs(' \
        --replace '../../../deps/${jsc-filename}.tar.gz' '${react-native-deps}/deps/${jsc-filename}.tar.gz' 
    '';

    # The ELF types are incompatible with the host platform, so let's not even try
    dontPatchELF = true;
    dontStripHost = true;
    noAuditTmpdir = true;

    # Take whole sources into consideration when calculating sha
    outputHashMode = "recursive";
    outputHashAlgo = "sha256";
  };

in {
  inherit deps react-native-deps;

  shellHook = d: ''
    # This avoids RN trying to download dependencies. Maybe we need to wrap this in a special RN environment derivation
    export REACT_NATIVE_DEPENDENCIES="${d.react-native-deps}/deps"

    ln -sf $STATUS_REACT_HOME/mobile_files/package.json.orig $STATUS_REACT_HOME/package.json
    ln -sf $STATUS_REACT_HOME/mobile_files/metro.config.js $STATUS_REACT_HOME/metro.config.js
    rm -f $STATUS_REACT_HOME/yarn.lock

    export PATH="$STATUS_REACT_HOME/node_modules/.bin:$PATH"
  '';
}
