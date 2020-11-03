{ project ? import ./nix {}
}:

project.pkgs.mkShell {
  CLANG_PATH = project.clang + "/bin/clang";
  CLANGPP_PATH = project.clang + "/bin/clang++";

  buildInputs = builtins.attrValues project.devTools;
  shellHook = ''
    ${project.ci.pre-commit-check.shellHook}
  '';
}
