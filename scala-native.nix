{ project ? import ./nix { }
}:

project.pkgs.stdenv.mkDerivation rec {
  pname = "scala-native";
  version = "0.4.1-SNAPSHOT";

  src = project.pkgs.fetchFromGitHub {
    owner = "scala-native";
    repo = pname;
    rev = "4270a34a56c0797098c3705d4b9ce85f7a79cf32";
    sha256 = "0lzjqh4l25nywjvr306f8crkx9v0ssvrbq8wa3ky6di8l4kmdic6";
  };

  deps =
    let
      name = "${pname}-${version}";
    in
    project.pkgs.stdenv.mkDerivation {
      inherit src;

      name = "${name}-scala-native";

      patches = [ ./toClass.diff ];

      nativeBuildInputs = [ project.pkgs.sbt ];

      dontStrip = true;
      outputHashAlgo = "sha256";
      outputHash = "0s84dyfrqz24vjkaf3d271qgr2q95rvglwcnin5y006hv09waryc";
      outputHashMode = "recursive";

      preHook = ''
        export HOME="$NIX_BUILD_TOP"
        export USER="nix"
        export COURSIER_CACHE="$HOME/coursier"
      '';

      preBuild = ''
        # pretend this is scala-native 0.4.0
        substituteInPlace nir/src/main/scala/scala/scalanative/nir/Versions.scala --replace '0.4.1-SNAPSHOT' '0.4.0'
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
