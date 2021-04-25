{ project ? import ./nix { }
}:

project.pkgs.stdenv.mkDerivation rec {
  pname = "scala-native";
  version = "0.4.1-SNAPSHOT";

  src = project.pkgs.fetchFromGitHub {
    owner = "scala-native";
    repo = pname;
    rev = "202c894ad85c155d48f041d78675318bd7246980";
    sha256 = "1y4vhc6piywg0rlfi37hnadlczlcir2pnx5zdsgkpyl2jac8i217";
  };

  deps =
    let
      name = "${pname}-${version}";
    in
    project.pkgs.stdenv.mkDerivation {
      inherit src;

      name = "${name}-scala-native";

      nativeBuildInputs = [ project.pkgs.sbt ];

      dontStrip = true;
      outputHashAlgo = "sha256";
      outputHash = "0cbvbl3gijn4yma6kxl9w8iixddwvh2xz35ks8ik6kl8bzm2bfcl";
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

        sbt --sbt-dir "$HOME/sbt" --ivy "$HOME/.ivy2" --batch publishLocal '++2.13.4' \
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
