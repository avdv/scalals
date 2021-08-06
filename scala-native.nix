{ project ? import ./nix { }
}:

let
  stdenv = project.pkgs.stdenvNoCC;
in

stdenv.mkDerivation rec {
  pname = "scala-native";
  version = "0.4.1-SNAPSHOT";

  src = project.pkgs.fetchFromGitHub {
    owner = "scala-native";
    repo = pname;
    rev = "32bc2ea124ad40a80dbd19a51fc1dc61c416e043";
    sha256 = "1cy0kr7jazghvlx7rvid7my6a2n7sh5qg579xsyyl1dzddqc9yhx";
  };

  deps =
    let
      name = "${pname}-${version}";
    in
    stdenv.mkDerivation {
      inherit src;

      name = "${name}-scala-native";

      patches = [ ./toClass.diff ./stableNirOutput.diff ];

      nativeBuildInputs = [ project.sbt ];

      dontStrip = true;
      outputHashAlgo = "sha256";
      outputHash = "18lngqjdcwxf355l0diir753z64salfknypvb0j2plxqaay5l49w";
      outputHashMode = "recursive";

      preHook = ''
        export HOME="$NIX_BUILD_TOP"
        export USER="nix"
        export COURSIER_CACHE="$HOME/coursier"
      '';

      # set publication time to fixed value and re-compute hashes
      postFixup = ''
        find "$out" -type f -name 'ivy.xml' | while read IVYXML; do
          sed -i -e '/<info/s/publication="[^"]*"/publication="20210425113142"/' "$IVYXML"
          if [ -f "$IVYXML.md5" ]; then
             md5sum "$IVYXML" | cut -f1 -d' ' > "$IVYXML.md5"
          fi
          if [ -f "$IVYXML.sha1" ]; then
             sha1sum "$IVYXML" | cut -f1 -d' ' > "$IVYXML.sha1"
          fi
        done
      '';

      buildPhase = ''
        runHook preBuild

        export SCALANATIVE_MODE=release-fast

        sbt -Dsbt.supershell=false --sbt-dir "$HOME/sbt" --ivy "$HOME/.ivy2" --batch '++2.13.6' \
            nscplugin/publishLocal \
            junitPlugin/publishLocal \
            nativelib/publishLocal \
            clib/publishLocal \
            posixlib/publishLocal \
            javalib/publishLocal \
            auxlib/publishLocal \
            scalalib/publishLocal \
            windowslib/publishLocal \
            testInterfaceSbtDefs/publishLocal \
            testInterface/publishLocal \
            junitRuntime/publishLocal

         sbt -Dsbt.supershell=false --sbt-dir "$HOME/sbt" --ivy "$HOME/.ivy2" --batch \
            util/publishLocal \
            nir/publishLocal \
            tools/publishLocal \
            testRunner/publishLocal \
            sbtScalaNative/publishLocal
      '';

      installPhase = ''
        mkdir "$out"
        cp --recursive --target-directory="$out" "$HOME/.ivy2/local/org.scala-native"
      '';
    };

  installPhase = ''
    runHook preBuild

    mkdir "$out"

    cat >"$out/repositories" <<EOF
    [repositories]
       r1: file://${deps}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
    EOF
  '';

  preHook = ''
    export HOME="$NIX_BUILD_TOP"
    export USER="nix"
  '';

  nativeBuildInputs = with project.pkgs; [ sbt ];
}
