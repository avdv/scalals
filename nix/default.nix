{ sources ? import ./sources.nix
}:
let
  # default nixpkgs
  pkgs = import sources.nixpkgs { };

  sbt = pkgs.sbt.overrideAttrs (
    _: rec {
      patchPhase = ''echo -java-home ${pkgs.openjdk11_headless} >> conf/sbtopts'';
    }
  );

  # gitignore.nix 
  gitignoreSource = (import sources."gitignore.nix" { inherit (pkgs) lib; }).gitignoreSource;

  src = gitignoreSource ./..;
in
{
  inherit pkgs src sbt;

  # provided by shell.nix
  devTools = {
    inherit sbt;
    inherit (pkgs) git niv pre-commit tinycc which;
  };

  # to be built by github actions
  ci = {
    pre-commit-check = (import sources."pre-commit-hooks.nix").run {
      inherit src;
      hooks = {
        shellcheck.enable = true;
        nixpkgs-fmt.enable = true;
        nix-linter.enable = true;
      };
      # generated files / submodules
      excludes = [
        "^nix/sources\.nix$"
        "^modules/"
      ];
    };
  };
}
