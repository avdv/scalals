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
    rev = "7997e294c3d29aeb4c32d8b91b8e91062f667e1f";
    sha256 = "1sn01jbn0w9p6dnj726q242abm9lnl4wykryyih17fjmicn4mghv";
  };

  deps =
    let
      name = "${pname}-${version}";
    in
    stdenv.mkDerivation {
      inherit src;

      name = "${name}-scala-native";

      patches = [ ./toClass.diff ./stableNirOutput.diff ];

      nativeBuildInputs = [ project.pkgs.sbt ];

      dontStrip = true;
      outputHashAlgo = "sha256";
      outputHash = "1k1wszyjvn20wp6873hzlri5i8pjqcj795asd0iy38sykc992lh5";
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

        sbt --sbt-dir "$HOME/sbt" --ivy "$HOME/.ivy2" --batch publishLocal '++2.13.6' \
            auxlib/publishLocal \
            clib/publishLocal \
            javalib/publishLocal \
            javalibExtDummies/publishLocal \
            junitAsyncJVM/publishLocal \
            junitAsyncNative/publishLocal \
            junitPlugin/publishLocal \
            junitRuntime/publishLocal \
            junitTestOutputsJVM/publishLocal \
            junitTestOutputsNative/publishLocal \
            nativelib/publishLocal \
            nir/publishLocal \
            nscplugin/publishLocal \
            posixlib/publishLocal \
            sandbox/publishLocal \
            scalalib/publishLocal \
            testInterface/publishLocal \
            testInterfaceSbtDefs/publishLocal \
            testRunner/publishLocal \
            testingCompiler/publishLocal \
            testingCompilerInterface/publishLocal \
            tests/publishLocal \
            testsExt/publishLocal \
            tools/publishLocal \
            util/publishLocal
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
