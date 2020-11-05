{ project ? import ./. { }
}:
let
  name = "scalals";
  src = ./..;

  inherit (project) pkgs;

  #sbtOptions = "--sbt-dir $out/sbt --ivy $out/ivy --sbt-boot $out";

  # SBT_CREDENTIALS file containing
  #
  # realm=My Nexus Repository Manager
  # host=my.artifact.repo.net
  # user=admin
  # password=admin123

  # -Dsbt.override.build.repos=true

  # -Dsbt.repository.config=<path-to-your-repo-file>

  # 1. generate repo file using a list of other files...

  # 2. generate credentials file using a list of credential files

  # scala-native = let
  #   name = "scalals-scalanative";

  #   src = pkgs.stdenv.fetchFromGitHub {
  #     owner = "avdv";
  #     repo = "scala-native";
  #     rev = "c518195657c54b5675e87966a488c36823484b05";
  #     sha256 = "1zhglsx3b5k6np3ppfkkrqz9wg0j7ip598xxfgn75gjl020w0cax";
  #   };

  #   deps = pkgs.stdenv.mkDerivation {
  #     name = "${name}-sbt";


  #   };
  # in pkgs.stdenv.mkDerivation {
  #   inherit name;

  #   buildPhase = ''
  #     mkdir 
  #     sbt --batch 
  #     '';
  # };

  deps = pkgs.stdenv.mkDerivation {
    name = "${name}-deps";

    inherit src;

    nativeBuildInputs = builtins.attrValues project.devTools;

    buildPhase = ''
      mkdir -p $out/{ivy,sbt,boot,coursier}

      export COURSIER_CACHE=$out/coursier

      export SN_BUILD=$( sed -n -e 's/.* SN_BUILD=\([0-9]*\).*/\1/p' -e T -e q .travis.yml )
      export SBT_VERSION=$( sed -n -e 's/.* SBT_VERSION=\([0-9.]*\).*/\1/p' -e T -e q .travis.yml )
      export TRAVIS_SCALA_VERSION=$( sed -n -e '/^scala:/{ n ; s/^[- ]*//p ; q }' .travis.yml )

      export JAVA_OPTS="-Dsbt.global.base=$out/sbt -Dsbt.ivy.home=$out/ivy"
      export SBT_OPTS="--sbt-dir $out/sbt --sbt-boot $out/boot"

      source scripts/travis-install

      sbt --batch update
    '';

    outputHashAlgo = "sha256";
    outputHashMode = "recursive";
    outputHash = "1v5a76pvk7llbyv2rg50wlxc2wf468l2cslz1vi20aihycbyky7x";
  };

  rtrim = let inherit (pkgs.lib.strings) hasSuffix removeSuffix; in
    s: if hasSuffix "\n" s then rtrim (removeSuffix "\n" s) else s
  ;
in
pkgs.stdenv.mkDerivation {
  inherit name;
  version = rtrim (builtins.readFile ../VERSION);

  inherit src;

  buildPhase = ''
    echo sbt --batch --sbt-dir ${deps}/sbt --ivy ${deps}/ivy --sbt-boot ${deps}/boot 'set offline := true'
  '';

  meta = with pkgs.stdenv.lib; {
    homepage = "https://github.com/avdv/scalals";
    description = "A colorful `ls` command with icons";
    # license = licenses.;
    # maintainers = ...;
    platforms = platforms.linux;
  };
}
