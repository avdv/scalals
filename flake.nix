{
  description = "scalals";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";
    nix-filter.url = "github:numtide/nix-filter";
    flake-compat = {
      url = "github:edolstra/flake-compat";
      flake = false;
    };
    flake-utils.url = "github:numtide/flake-utils";
    git-hooks = {
      inputs = {
        nixpkgs.follows = "nixpkgs";
      };
      url = "github:cachix/git-hooks.nix";
    };
    sbt = {
      inputs = {
        nixpkgs.follows = "nixpkgs";
        flake-utils.follows = "flake-utils";
      };
      url = "github:zaninime/sbt-derivation";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      nix-filter,
      flake-utils,
      git-hooks,
      sbt,
      ...
    }:
    flake-utils.lib.eachSystem
      [
        "aarch64-linux"
        "aarch64-darwin"
        "x86_64-darwin"
        "x86_64-linux"
      ]
      (
        system:
        let
          filter = nix-filter.lib;

          jreHeadlessOverlay = _: prev: { jre = prev.openjdk11_headless; };

          scalafmtOverlay = final: prev: {
            scalafmt = prev.scalafmt.overrideAttrs (
              old:
              let
                version = builtins.head (
                  builtins.match ''[ \n]*version *= *"([^ \n]+)".*'' (builtins.readFile ./.scalafmt.conf)
                );
                outputHash = "sha256-vR6bI9HsTJNbdq4BAnv4/LmV00csS1QrnxW8nRsX1/M=";
              in
              {
                inherit version;
                passthru = {
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
              }
            );
          };

          pkgs = import nixpkgs {
            inherit system;
            overlays = [
              jreHeadlessOverlay
              scalafmtOverlay
            ];
          };

          inherit (pkgs) lib stdenvNoCC zig_0_12;

          zig = zig_0_12;

          mkShell = pkgs.mkShell.override { stdenv = stdenvNoCC; };

          clang = pkgs.writeScriptBin "clang" ''
            #!${pkgs.bash}/bin/bash

            declare -a args
            for arg; do
              arg="''${arg/-unknown-/-}"
              arg="''${arg/-apple-darwin-none/-macos-none}"
              args+=( "$arg" )
            done
            exec ${zig}/bin/zig cc "''${args[@]}"
          '';

          clangpp = pkgs.writeShellApplication {
            name = "clang++";
            runtimeInputs = [ pkgs.gnused ];
            text = ''
              declare -a args=()
              declare -a tmpfiles=()
              trap '[[ "''${#tmpfiles[@]}" -gt 0 ]] && rm -v "''${tmpfiles[@]}"' EXIT
              for arg; do
                 case "$arg" in
                   @* )
                     infile="''${arg:1}"
                     resp=$( mktemp )
                     tmpfiles+=( "$resp" )
                     sed -e 's,-unknown-,-,' -e 's,-apple-darwin-none,-macos-none,' "$infile" >> "$resp"
                     args+=( "@$resp" )
                     ;;
                   * )
                     arg="''${arg/-unknown-/-}"
                     arg="''${arg/-apple-darwin-none/-macos-none}"
                     args+=( "$arg" )
                 esac
              done
              ${zig}/bin/zig c++ "''${args[@]}"
            '';
          };

          nativeBuildInputs = with pkgs; [
            git
            ninja
            zig
            which
            clang
            clangpp
          ];
        in
        rec {
          formatter = pkgs.nixfmt-rfc-style;

          packages = rec {
            inherit (pkgs) scalafmt;

            scalals = sbt.lib.mkSbtDerivation rec {
              inherit pkgs nativeBuildInputs;

              overrides = {
                stdenv = stdenvNoCC;
              };

              pname = "scalals-native";

              # read the first non-empty string from the VERSION file
              version = builtins.head (builtins.match "[ \n]*([^ \n]+).*" (builtins.readFile ./VERSION));

              depsSha256 = "sha256-WhH2QsBRMAPdvk7A4ONZC8Ptqj72oDYwiXcT5S97ntw=";

              src = filter {
                root = self;
                include = [
                  "native"
                  "project"
                  "shared"
                  ./.jvmopts
                  ./build.sbt
                ];
              };

              # explicitly override version from sbt-dynver which does not work within a nix build
              patchPhase = ''
                echo 'ThisBuild / version := "${version}"' > version.sbt
              '';

              env = {
                SCALANATIVE_MODE = "release-full"; # {debug, release-fast, release-full}
                SCALANATIVE_LTO = if stdenvNoCC.isLinux then "thin" else "none"; # {none, full, thin}
                XDG_CACHE_HOME = "xdg_cache"; # needed by zig cc for a writable directory

                NIX_CFLAGS_COMPILE =
                  if stdenvNoCC.isLinux && stdenvNoCC.isx86_64 then
                    # zig uses -march=native by default
                    "-march=sandybridge"
                  else if stdenvNoCC.isDarwin then
                    # need to set target explicitly, see https://github.com/ziglang/zig/issues/14651
                    "-target ${stdenvNoCC.hostPlatform.qemuArch}-macos.11.0-none"
                  else
                    null;
              };

              buildPhase = ''
                sbt tpolecatReleaseMode 'project scalalsNative' 'show nativeConfig' ninjaCompile ninja
                ninja -f native/target/build.ninja
              '';

              dontPatchELF = true;

              depsWarmupCommand = "sbt tpolecatDevMode scalalsNative/compile";

              installPhase = ''
                mkdir --parents $out/bin
                cp "$(ninja -f native/target/build.ninja -t targets rule exe)" $out/bin/scalals
              '';
            };
            default = scalals;
          };

          apps.default = flake-utils.lib.mkApp {
            drv = packages.default;
            exePath = "/bin/scalals";
          };

          checks = {
            pre-commit-check = git-hooks.lib.${system}.run {
              src = ./.;
              hooks = {
                nix-fmt = {
                  enable = true;
                  name = "nix fmt";
                  entry = "${pkgs.nixfmt-rfc-style}/bin/nixfmt";
                  types = [ "nix" ];
                };
                scalafmt = {
                  enable = true;
                  name = "scalafmt";
                  entry = "${pkgs.scalafmt}/bin/scalafmt --respect-project-filters";
                  types = [ "scala" ];
                };
              };
            };
          };

          devShells =
            {
              default = mkShell {
                name = "scalals";

                env.SBT_TPOLECAT_DEV = "1";

                shellHook = ''
                  ${checks.pre-commit-check.shellHook}
                '';
                packages = [ pkgs.metals ];
                nativeBuildInputs = nativeBuildInputs ++ [ pkgs.sbt ];
              };

              graalVM = pkgs.mkShell {
                name = "scalals / graalvm";

                # Workaround GraalVM issue where the builder does not have access to the
                # environment variables since 21.0.0
                # https://github.com/oracle/graal/pull/6095
                # https://github.com/oracle/graal/pull/6095
                # https://github.com/oracle/graal/issues/7502
                env.NATIVE_IMAGE_DEPRECATED_BUILDER_SANITATION = "true";

                shellHook = ''
                  ${checks.pre-commit-check.shellHook}
                '';
                nativeBuildInputs = [
                  pkgs.graalvm-ce
                  pkgs.sbt
                ];
              };
            }
            // (lib.optionalAttrs (system == "x86_64-linux") (
              let
                inherit (pkgs.pkgsCross.aarch64-multiplatform-musl) llvmPackages_13 mkShell;
              in
              {
                aarch64-cross = mkShell.override { stdenv = llvmPackages_13.libcxxStdenv; } {
                  name = "scalals-arm64";

                  env.NIX_CFLAGS_LINK = "-static";

                  nativeBuildInputs = [
                    pkgs.lld_13
                    pkgs.git
                    pkgs.ninja
                    pkgs.which
                    pkgs.sbt
                  ];
                };
              }
            ));

          # compatibility for nix < 2.7.0
          defaultApp = apps.default;
          defaultPackage = packages.default;
          devShell = devShells.default;
        }
      );
}
