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

          gcceh-overlay = final: prev:
            let
              libcxxabi = prev.pkgsStatic.llvmPackages_16.libcxxabi.overrideAttrs (old: {
                buildInputs = old.buildInputs ++ [ empty-gcc-eh ];
              });
            in
            {
              pkgsStatic = prev.pkgsStatic // {
                llvmPackages_16 = prev.pkgsStatic.llvmPackages_16 // {
                  inherit libcxxabi;
                };
              };
            };

          scalafmtOverlay = final: prev: {
            scalafmt = prev.scalafmt.overrideAttrs (old:
              let
                version = builtins.head (builtins.match ''[ \n]*version *= *"([^ \n]+)".*'' (builtins.readFile ./.scalafmt.conf));
                outputHash = "sha256-aIuAHL/LUpSZzGPe7qZ7LsgQ2WX7pBHwiFjilkPxXOY=";
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

          # temporary workaround for darwin https://github.com/NixOS/nixpkgs/issues/235757
          strip-nondet-overlay = final: prev: {
            strip-nondeterminism = prev.strip-nondeterminism.overrideAttrs (old: {
              buildInputs = old.buildInputs ++ [ prev.perlPackages.SubOverride ];
            });
          };

          pkgs = import nixpkgs { inherit system; overlays = [ jreHeadlessOverlay scalafmtOverlay strip-nondet-overlay gcceh-overlay ]; };

          inherit (pkgs) lib pkgsStatic;

          lld = pkgs.lld_16;

          # pkgStatic is not yet supported on Darwin (see https://github.com/NixOS/nixpkgs/issues/270375)
          theStdenv =
            if pkgs.stdenv.isDarwin then
              pkgs.llvmPackages_16.libcxxStdenv
            else
              pkgsStatic.llvmPackages_16.libcxxStdenv;

          mkShell =
            (if pkgs.stdenv.isDarwin then
              pkgs.mkShell
            else
              pkgsStatic.mkShell
            ).override { stdenv = theStdenv; };

          nativeBuildInputs = with pkgs; [ git lld ninja which ];

          empty-gcc-eh = pkgs.runCommand "empty-gcc-eh" { } ''
            if $CC -Wno-unused-command-line-argument -x c - -o /dev/null <<< 'int main() {}'; then
              echo "linking succeeded; please remove empty-gcc-eh workaround" >&2
              exit 3
            fi
            mkdir -p $out/lib
            ${pkgs.binutils-unwrapped}/bin/ar r $out/lib/libgcc_eh.a
          '';
        in
        rec {
          packages = rec {
            inherit (pkgs) scalafmt;

            llvm = pkgsStatic.llvmPackages_16.libcxxStdenv;

            scalals = sbt.lib.mkSbtDerivation rec {
              inherit pkgs nativeBuildInputs;

              overrides = { stdenv = theStdenv; };

              pname = "scalals-native";

              # read the first non-empty string from the VERSION file
              version = builtins.head (builtins.match "[ \n]*([^ \n]+).*" (builtins.readFile ./VERSION));

              depsSha256 = "sha256-Rk+7efdsX1hCr9pKDC9VMO7tIfyn2XHUOq9Asec5OfQ=";

              src = ./.;

              # explicitly override version from sbt-dynver which does not work within a nix build
              patchPhase = ''
                echo 'ThisBuild / version := "${version}"' > version.sbt
              '';

              SCALANATIVE_MODE = "release-full"; # {debug, release-fast, release-full}
              SCALANATIVE_LTO = "thin"; # {none, full, thin}

              buildPhase = ''
                export NIX_LDFLAGS="$NIX_LDFLAGS -L${empty-gcc-eh}/lib"

                sbt 'project scalalsNative' 'show nativeConfig' nativeLink
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
                export NIX_LDFLAGS="$NIX_LDFLAGS -L${empty-gcc-eh}/lib"

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
