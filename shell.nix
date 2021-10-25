{ project ? import ./nix { }
}:
let
  pkgs = project.pkgs.pkgsStatic;
  stdenv = pkgs.llvmPackages_11.libcxxStdenv;
  mkShell = pkgs.mkShell.override { inherit stdenv; };
in
mkShell {
  nativeBuildInputs = builtins.attrValues project.devTools;

  shellHook = ''
    export CLANG_PATH="$NIX_CC/bin/$CC"
    export CLANGPP_PATH="$NIX_CC/bin/$CXX"

    ${project.ci.pre-commit-check.shellHook}
  '';

}
