{
  description = "scalals";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-23.11";
    flake-compat = {
      url = "github:edolstra/flake-compat";
      flake = false;
    };
    flake-utils.url = "github:numtide/flake-utils";
    pre-commit-hooks = {
      inputs = {
        nixpkgs.follows = "nixpkgs";
        flake-utils.follows = "flake-utils";
      };
      url = "github:cachix/pre-commit-hooks.nix";
    };
    sbt = {
      inputs = {
        nixpkgs.follows = "nixpkgs";
        flake-utils.follows = "flake-utils";
      };
      url = "github:zaninime/sbt-derivation";
    };
  };

  outputs = { nixpkgs, flake-utils, pre-commit-hooks, sbt, ... }:
    flake-utils.lib.eachSystem [ "aarch64-linux" "aarch64-darwin" "x86_64-darwin" "x86_64-linux" ]
      (system:
        let
          jreHeadlessOverlay = _: prev: {
            jre = prev.openjdk11_headless;
          };

          scalafmtOverlay = final: prev: {
            scalafmt = prev.scalafmt.overrideAttrs (old:
              let
                version = builtins.head (builtins.match ''[ \n]*version *= *"([^ \n]+)".*'' (builtins.readFile ./.scalafmt.conf));
                outputHash = "sha256-Zw7DwHVj/A6S6QLDqWnFGg7vGboJQMDHlsy1ueSGG4c=";
              in
              {
                inherit version;
                passthru =
                  {
                    inherit outputHash;
                  };
                buildInputs = [
                  (prev.stdenv.mkDerivation {
                    name = "scalafmt-deps-${version}";
                    buildCommand = ''
                      export COURSIER_CACHE=$(pwd)
                      ${prev.coursier}/bin/cs fetch org.scalameta:scalafmt-cli_2.13:${version} > deps
                      mkdir -p $out/share/java
                      cp $(< deps) $out/share/java/
                    '';
                    outputHashMode = "recursive";
                    inherit outputHash;
                    outputHashAlgo = if outputHash == "" then "sha256" else null;
                  })
                ];
              });
          };

          pkgs = import nixpkgs { inherit system; overlays = [ jreHeadlessOverlay scalafmtOverlay ]; };

          inherit (pkgs) lib zig stdenvNoCC;

          mkShell = pkgs.mkShell.override { stdenv = stdenvNoCC; };

          clang = pkgs.writeScriptBin "clang" ''
            exec ${zig}/bin/zig cc "$@"
          '';

          clangpp = pkgs.writeScriptBin "clang++" ''
            exec ${zig}/bin/zig c++ "$@"
          '';

          nativeBuildInputs = with pkgs; [ git ninja zig which clang clangpp ];
        in
        rec {
          packages = rec {
            inherit (pkgs) scalafmt;

            scalals = sbt.lib.mkSbtDerivation rec {
              inherit pkgs nativeBuildInputs;

              overrides = { stdenv = stdenvNoCC; };

              pname = "scalals-native";

              # read the first non-empty string from the VERSION file
              version = builtins.head (builtins.match "[ \n]*([^ \n]+).*" (builtins.readFile ./VERSION));

              depsSha256 = "sha256-f18NA2dkzGq5p+OnFZ8OIcqAayP46pXwMJDzbI2tTAE=";

              src = ./.;

              # explicitly override version from sbt-dynver which does not work within a nix build
              patchPhase = ''
                echo 'ThisBuild / version := "${version}"' > version.sbt
              '';

              SCALANATIVE_MODE = "release-full"; # {debug, release-fast, release-full}
              SCALANATIVE_LTO = "thin"; # {none, full, thin}
              XDG_CACHE_HOME = "xdg_cache"; # needed by zig cc for a writable directory

              NIX_CFLAGS_COMPILE = pkgs.lib.optional (with pkgs.stdenv; isLinux && isx86_64) "-march=sandybridge";

              buildPhase = ''
                sbt tpolecatReleaseMode 'project scalalsNative' 'show nativeConfig' ninjaCompile ninja
                ninja -f native/target/scala-3.4.1/native/build.ninja
              '';

              dontPatchELF = true;

              depsWarmupCommand = "sbt scalalsNative/compile";

              installPhase = ''
                mkdir --parents $out/bin
                cp native/target/scala-*/scalals-out $out/bin/scalals
              '';
            };
            default = scalals;
          };

          apps.default = flake-utils.lib.mkApp { drv = packages.default; exePath = "/bin/scalals"; };

          checks = {
            pre-commit-check = pre-commit-hooks.lib.${system}.run {
              src = ./.;
              hooks = {
                nixpkgs-fmt.enable = true;
                scalafmt = {
                  enable = true;
                  name = "scalafmt";
                  entry = "${pkgs.scalafmt}/bin/scalafmt --respect-project-filters";
                  types = [ "scala" ];
                };
              };
            };
          };

          devShells = {
            default = mkShell {
              name = "scalals";
              shellHook = ''
                ${checks.pre-commit-check.shellHook}
              '';
              packages = [ pkgs.metals ];
              nativeBuildInputs = nativeBuildInputs ++ [ pkgs.sbt ];
            };

            graalVM = pkgs.mkShell {
              name = "scalals / graalvm";
              shellHook = ''
                ${checks.pre-commit-check.shellHook}
              '';
              nativeBuildInputs = [ pkgs.graalvm-ce pkgs.sbt ];
            };
          } // (lib.optionalAttrs (system == "x86_64-linux") (
            let
              inherit (pkgs.pkgsCross.aarch64-multiplatform-musl) llvmPackages_13 mkShell;
            in
            {
              aarch64-cross = mkShell.override { stdenv = llvmPackages_13.libcxxStdenv; } {
                name = "scalals-arm64";

                NIX_CFLAGS_LINK = "-static";
                nativeBuildInputs = [ pkgs.lld_13 pkgs.git pkgs.ninja pkgs.which pkgs.sbt ];
              };
            }
          )
          );

          # compatibility for nix < 2.7.0
          defaultApp = apps.default;
          defaultPackage = packages.default;
          devShell = devShells.default;
        }
      );
}
