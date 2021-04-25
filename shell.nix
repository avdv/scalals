{ project ? import ./nix { }
}:
let
  scala-native = import ./scala-native.nix { inherit project; };
in
project.pkgs.mkShell {
  CLANG_PATH = project.clang + "/bin/clang";
  CLANGPP_PATH = project.clang + "/bin/clang++";

  buildInputs = builtins.attrValues project.devTools;
  shellHook = ''
    ${project.ci.pre-commit-check.shellHook}
    mkdir --parents "$HOME/.ivy2/local"
    ln --symbolic --force --target-directory="$HOME/.ivy2/local" "${scala-native.deps}/org.scala-native"
  '';

}
