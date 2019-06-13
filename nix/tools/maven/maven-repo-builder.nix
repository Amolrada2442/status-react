{ stdenvNoCC, lib, writeShellScriptBin, fetchurl }:

# Put the downloaded files in a fake Maven repository
name: source:

let
  script = writeShellScriptBin "create-local-maven-repo" (''
    mkdir -p $out
    cd $out
  '' +
  (lib.concatMapStrings (dep: 
    let
      url = "${dep.host}/${dep.path}";
      pom = {
        sha1 = lib.attrByPath [ "pom" "sha1" ] "" dep;
        sha256 = lib.attrByPath [ "pom" "sha256" ] "" dep;
      };
      pom-download = if pom.sha256 != "" then fetchurl { url = "${url}.pom"; inherit (pom) sha256; } else "";
      jar = {
        sha1 = lib.attrByPath [ "jar" "sha1" ] "" dep;
        sha256 = lib.attrByPath [ "jar" "sha256" ] "" dep;
      };
      jar-download = if jar.sha256 != "" then fetchurl { url = "${url}.${dep.type}"; inherit (jar) sha256; } else "";
      fileName = lib.last (lib.splitString "/" dep.path);
      directory = lib.removeSuffix fileName dep.path;
    in
      ''
        mkdir -p ${directory}
      '' +
      (lib.optionalString (pom-download != "") ''
        cp "${pom-download}" "${dep.path}.pom"
      '') +
      (lib.optionalString (pom.sha1 != "") ''
        echo "${pom.sha1}" > "${dep.path}.pom.sha1"
      '') +
      (lib.optionalString (jar-download != "") ''
        cp "${jar-download}" "${dep.path}.${dep.type}"
      '') +
      (lib.optionalString (jar.sha1 != "") ''
        echo "${jar.sha1}" > "${dep.path}.jar.sha1"
      ''))
    (lib.attrValues source)));

in stdenvNoCC.mkDerivation {
  inherit name;
  phases = [ "buildPhase" ];
  buildPhase = "${script}/bin/create-local-maven-repo";
}
