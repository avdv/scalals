{ ... }:
{
  # Used to find the project root
  projectRootFile = "build.sbt";
  programs = {
    actionlint.enable = true;
    nixfmt.enable = true;
    scalafmt.enable = true;
    shfmt.enable = true;
    yamlfmt.enable = true;
  };
}
