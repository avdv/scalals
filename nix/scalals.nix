{ sources ? import ./sources.nix
}:
let
  # default nixpkgs
  pkgs = import sources.nixpkgs { };

  rtrim = let inherit (pkgs.lib.strings) hasSuffix removeSuffix; in
    s: if hasSuffix "\n" s then rtrim (removeSuffix "\n" s) else s
  ;
in
pkgs.stdenv.mkDerivation rec {
  pname = "scalals";
  version = rtrim (builtins.readFile ../VERSION);

  src = ../native/target/scala-2.11;

  installPhase = ''
    mkdir -p $out/bin
    cp scalals-out $out/bin/scalals
  '';
}
