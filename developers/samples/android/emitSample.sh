# Abort on error
set -e
printf "\033[1;34mProcessing: ${1} ...\033[0m\n"
pushd ${1}
printf "\033[0;33m[Refresh]\033[0m\n"
./gradlew refresh
printf "\033[0;33m[Clean 1]\033[0m\n"
find . -name build -exec rm -rf {} \; || true
printf "\033[0;33m[EmitGradle]\033[0m\n"
./gradlew emitGradle
printf "\033[0;33m[Clean 2]\033[0m\n"
find . -name build -exec rm -rf {} \; || true
printf "\033[0;33m[EmitBrowseable]\033[0m\n"
./gradlew emitBrowseable
printf "\033[0;33m[Clean 3]\033[0m\n"
find . -name build -exec rm -rf {} \; || true
printf "\033[0;33m[EmitGradleZip]\033[0m\n"
./gradlew emitGradleZip
popd
