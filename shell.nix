{ project ? import ./nix { }
}:

project.pkgs.mkShell {
  CLANG_PATH = project.clang + "/bin/clang";
  CLANGPP_PATH = project.clang + "/bin/clang++";

  buildInputs = builtins.attrValues project.devTools;
  shellHook = ''
    ${project.ci.pre-commit-check.shellHook}

    export SN_BUILD=$( sed -n -e 's/.* SN_BUILD=\([0-9]*\).*/\1/p' -e T -e q .travis.yml )
    export SBT_VERSION=$( sed -n -e 's/.* SBT_VERSION=\([0-9.]*\).*/\1/p' -e T -e q .travis.yml )
    export TRAVIS_SCALA_VERSION=$( sed -n -e '/^scala:/{ n ; s/^[- ]*//p ; q }' .travis.yml )
  '';
}
