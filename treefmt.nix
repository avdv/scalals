{ ... }:
{
  # Used to find the project root
  projectRootFile = "build.sbt";
  programs = {
    nixfmt.enable = true;
    scalafmt.enable = true;
    shfmt.enable = true;
  };
}
