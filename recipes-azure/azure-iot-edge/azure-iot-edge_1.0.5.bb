DESCRIPTION = "Azure IoT Edge"

require azure-iot-edge.inc
require azure-iot-edge-legacy.inc

inherit cmake pkgconfig

DEPENDS = "\
	glib-2.0 \
	azure-iot-sdk \
	nanomsg \
	libuv \
"

# Modbus
SRC_URI += "git://github.com/Azure/iot-gateway-modbus.git;destsuffix=git-modbus;name=modbus"
SRCREV_modbus = "7daeca02df909278c775112e04d8255240cda48c"

# SQLite
SRC_URI += "git://github.com/Azure/iot-gateway-sqlite.git;destsuffix=git-sqlite;name=sqlite"
SRCREV_sqlite = "4fbf57d0c674f6ad41372ff2ba4b86a1380f1111"

SRC_URI += "\
	file://0001-Skip-adding-test-dependencies-if-not-required.patch \
	file://0002-Fix-nanomsg-library-detection.patch \
	file://0003-Skip-parson-submodule-init.patch \
	file://0004-Include-parson-with-main-library.patch \
	file://0005-Use-shared-openssl.patch \
	file://0006-Use-shared-libuv.patch \
"

SRC_URI += "\
	file://azure-functions-sample.sh \
	file://ble-gateway-sample.sh \
	file://dynamically-add-module-sample.sh \
	file://hello-world-sample.sh \
	file://modbus-sample.sh \
	file://native-module-host-sample.sh \
	file://proxy-sample.sh \
	file://simulated-device-cloud-upload-sample.sh \
	file://sqlite-sample.sh \
	file://azure-functions-module.sh \
	file://ble-module.sh \
	file://hello-world-module.sh \
	file://identitymap-module.sh \
	file://iothub-module.sh \
	file://logger-module.sh \
	file://simulated-device-module.sh \
"

PR = "r2"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

# Default packages
PACKAGES = "\
	${PN} \
	${PN}-dev \
	${PN}-dbg \
	${PN}-modules \
	${PN}-modules-src \
	${PN}-samples \
	${PN}-samples-src \
	${PN}-dotnetcore \
	${PN}-java \
	${PN}-nodejs \
"

# Additional packages
PACKAGES += "\
	${PN}-module-modbus \
	${PN}-samples-modbus \
	${PN}-samples-src-modbus \
	${PN}-module-sqlite \
	${PN}-samples-sqlite \
	${PN}-samples-src-sqlite \
"

## Java ##
def get_jdk_arch(d):
    jdk_arch = d.getVar('TRANSLATED_TARGET_ARCH', True)

    if jdk_arch == "x86-64":
        jdk_arch = "amd64"
    elif jdk_arch == "powerpc":
        jdk_arch = "ppc"
    elif jdk_arch == "powerpc64":
        jdk_arch = "ppc64"
    elif (jdk_arch == "i486" or jdk_arch == "i586" or jdk_arch == "i686"):
        jdk_arch = "i386"

    return jdk_arch

def get_jdk_home(d):
    jdk_home = d.getVar("STAGING_LIBDIR", True)
    jdk_home += "/jvm/"

    if os.path.exists(jdk_home):
        for child in os.listdir(jdk_home):
            test_path = os.path.join(jdk_home, child)
            if os.path.isdir(test_path):
                jdk_home = test_path
                break

    return jdk_home

## Java ##
JAVA_LIB_DIR = "${B}/bindings/java/"
JDK_ARCH = "${@get_jdk_arch(d)}"
JDK_HOME = "${@get_jdk_home(d)}"

## Node.JS ##
NODE_LIB_DIR = "${B}/bindings/nodejs/"

## .NET Core ##
DOTNET_LIB_DIR = "${B}/bindings/dotnetcore/"

PACKAGECONFIG ??= "java nodejs dotnetcore bluetooth"

PACKAGECONFIG[java] = "-Denable_java_binding:BOOL=ON -DJDK_ARCH=${JDK_ARCH}, -Denable_java_binding:BOOL=OFF, openjdk-8"
PACKAGECONFIG[nodejs] = "-Denable_nodejs_binding:BOOL=ON, -Denable_nodejs_binding:BOOL=OFF, nodejs-native (>= 6.%) nodejs-shared (>= 6.%)"
PACKAGECONFIG[dotnetcore] = "-Denable_dotnet_core_binding:BOOL=ON, -Denable_dotnet_core_binding:BOOL=OFF, dotnet-native"
PACKAGECONFIG[bluetooth] = "-Denable_ble_module:BOOL=ON, -Denable_ble_module:BOOL=OFF, , bluez5"

EXTRA_OECMAKE = "-DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS:BOOL=ON -Dinstall_modules:BOOL=ON -Dinstall_executables:BOOL=ON -Drun_as_a_service:BOOL=OFF"

do_modules() {
	# Modbus Module
	cp -rf ${WORKDIR}/git-modbus/modules/modbus_read ${S}/modules
	cp -rf ${WORKDIR}/git-modbus/samples/modbus_sample ${S}/samples
	echo 'add_subdirectory(modbus_read)' >> ${S}/modules/CMakeLists.txt
	echo 'add_subdirectory(modbus_sample)' >> ${S}/samples/CMakeLists.txt

	# SQLite Module
	cp -rf ${WORKDIR}/git-sqlite/modules/sqlite ${S}/modules
	cp -rf ${WORKDIR}/git-sqlite/samples/sqlite_sample ${S}/samples
	echo 'add_subdirectory(sqlite)' >> ${S}/modules/CMakeLists.txt
	echo 'add_subdirectory(sqlite_sample)' >> ${S}/samples/CMakeLists.txt
}

addtask do_modules after do_unpack before do_patch

do_configure_prepend() {
	# Java
	if ${@bb.utils.contains('PACKAGECONFIG','java','true','false',d)}; then
		export JAVA_HOME="${JDK_HOME}"
	fi

	# Node.JS
	if ${@bb.utils.contains('PACKAGECONFIG','nodejs','true','false',d)}; then
		export NODE_INCLUDE="${STAGING_INCDIR_NATIVE}/node"
		export NODE_LIB="${STAGING_LIBDIR}"
	fi

	# .NET Core
	if ${@bb.utils.contains('PACKAGECONFIG','dotnetcore','true','false',d)}; then
		sed -i 's|\${CMAKE_CURRENT_BINARY_DIR}/\.\.|${S}|g' ${S}/CMakeLists.txt		
		sed -i 's|projects_to_test=\"$binding_path/Microsoft.Azure.Devices.Gateway.Tests/Microsoft.Azure.Devices.Gateway.Tests.csproj\"|projects_to_test=\"\"|g' ${S}/tools/build_dotnet_core.sh
	fi
}

do_compile_prepend() {
	# Java
	if ${@bb.utils.contains('PACKAGECONFIG','java','true','false',d)}; then
		export JAVA_HOME="${JDK_HOME}"
	fi

	# Node.JS
	if ${@bb.utils.contains('PACKAGECONFIG','nodejs','true','false',d)}; then
		export NODE_INCLUDE="${STAGING_INCDIR_NATIVE}/node"
		export NODE_LIB="${STAGING_LIBDIR}"
	fi

	# .NET Core
	if ${@bb.utils.contains('PACKAGECONFIG','dotnetcore','true','false',d)}; then
		${S}/tools/build_dotnet_core.sh --config Release
	fi
}

do_install_prepend() {
	# Fix sample module paths
	find ${S}/samples -type f -name "*.json" -exec sed -i 's|\.\./\.\./modules|${libdir}/azureiot/modules|g' {} +
	find ${S}/samples -type f -name "*.json" -exec sed -i 's|\./modules|${libdir}/azureiot/modules|g' {} +
	find ${S}/samples -type f -name "*.json" -exec sed -i 's|build/modules|${libdir}/azureiot/modules|g' {} +
	sed -i 's|build/samples/ble_gateway/ble_printer|\.|g' ${S}/samples/ble_gateway/src/*.json
}

do_install() {
    	# Core
    	install -d ${D}${libdir}
	install -m 0755 ${B}/core/libgateway.so ${D}${libdir}

	install -d ${D}${includedir}/azureiot
	install -m 0644 ${S}/core/inc/*.h ${D}${includedir}/azureiot
	install -m 0644 ${S}/core/inc/linux/*.h ${D}${includedir}/azureiot

	install -d ${D}${includedir}/azureiot/experimental
	install -m 0644 ${S}/core/inc/experimental/*.h ${D}${includedir}/azureiot/experimental

	install -d ${D}${includedir}/azureiot/module_loaders
	install -m 0644 ${S}/core/inc/module_loaders/dynamic_loader.h ${D}${includedir}/azureiot/module_loaders
	install -m 0644 ${S}/proxy/outprocess/inc/module_loaders/*.h ${D}${includedir}/azureiot/module_loaders
	
	if ${@bb.utils.contains('PACKAGECONFIG','java','true','false',d)}; then
		install -m 0644 ${S}/core/inc/module_loaders/java_loader.h ${D}${includedir}/azureiot/module_loaders
	fi

	# Native Proxy Gateway
	install -d ${D}${libdir}
	install -m 0755 ${B}/proxy/gateway/native/libproxy_gateway.so ${D}${libdir}

	install -d ${D}${includedir}/azureiot
	install -m 0644 ${S}/proxy/gateway/native/inc/*.h ${D}${includedir}/azureiot
	install -m 0644 ${S}/proxy/message/inc/*.h ${D}${includedir}/azureiot

	# Native Module Host
	install -d ${D}${libdir}
	install -m 0755 ${B}/proxy/modules/native_module_host/libnative_module_host.so ${D}${libdir}

	install -d ${D}${includedir}/azureiot
	install -m 0644 ${S}/proxy/modules/native_module_host/inc/*.h ${D}${includedir}/azureiot

	# Modules
	install -d ${D}${includedir}/azureiot/modules/common
	install -m 0644 ${S}/modules/common/*.h ${D}${includedir}/azureiot/modules/common

	install -d ${D}${exec_prefix}/src/azureiotedge/modules/common
	install -m 0644 ${S}/modules/common/*.h ${D}${exec_prefix}/src/azureiotedge/modules/common/

	# Azure Functions Module
	install -d ${D}${libdir}/azureiot/modules/azure_functions
	install -m 0755 ${B}/modules/azure_functions/libazure_functions.so ${D}${libdir}/azureiot/modules/azure_functions/

	install -d ${D}${includedir}/azureiot/modules/azure_functions
	install -m 0644 ${S}/modules/azure_functions/inc/*.h ${D}${includedir}/azureiot/modules/azure_functions

	install -d ${D}${exec_prefix}/src/azureiotedge/modules/azure_functions/src
	install -d ${D}${exec_prefix}/src/azureiotedge/modules/azure_functions/inc
	install -m 0644 ${S}/modules/azure_functions/src/*.c ${D}${exec_prefix}/src/azureiotedge/modules/azure_functions/src/
	install -m 0644 ${S}/modules/azure_functions/inc/*.h ${D}${exec_prefix}/src/azureiotedge/modules/azure_functions/inc/
	install -m 0755 ${WORKDIR}/azure-functions-module.sh ${D}${exec_prefix}/src/azureiotedge/modules/azure_functions/build.sh

	# BLE Module
	if [ -e ${B}/modules/ble/ ]; then
		install -d ${D}${libdir}/azureiot/modules/ble
		install -m 0755 ${B}/modules/ble/libble.so ${D}${libdir}/azureiot/modules/ble/
		install -m 0755 ${B}/modules/ble/libble_c2d.so ${D}${libdir}/azureiot/modules/ble/

		install -d ${D}${includedir}/azureiot/modules/ble
		install -m 0644 ${S}/modules/ble/inc/*.h ${D}${includedir}/azureiot/modules/ble
		install -m 0644 ${S}/modules/ble/deps/linux/dbus-bluez/inc/*.h ${D}${includedir}/azureiot/modules/ble

		install -d ${D}${exec_prefix}/src/azureiotedge/modules/ble/src
		install -d ${D}${exec_prefix}/src/azureiotedge/modules/ble/inc
		install -d ${D}${exec_prefix}/src/azureiotedge/modules/ble/deps/dbus-bluez/src
		install -d ${D}${exec_prefix}/src/azureiotedge/modules/ble/deps/dbus-bluez/inc
		install -m 0644 ${S}/modules/ble/src/*.c ${D}${exec_prefix}/src/azureiotedge/modules/ble/src/
		install -m 0644 ${S}/modules/ble/inc/*.h ${D}${exec_prefix}/src/azureiotedge/modules/ble/inc/
		install -m 0644 ${S}/modules/ble/src/*.c ${D}${exec_prefix}/src/azureiotedge/modules/ble/src/
		install -m 0644 ${S}/modules/ble/deps/linux/dbus-bluez/src/*.c ${D}${exec_prefix}/src/azureiotedge/modules/ble/deps/dbus-bluez/src/
		install -m 0644 ${S}/modules/ble/deps/linux/dbus-bluez/inc/*.h ${D}${exec_prefix}/src/azureiotedge/modules/ble/deps/dbus-bluez/inc/
		install -m 0755 ${WORKDIR}/ble-module.sh ${D}${exec_prefix}/src/azureiotedge/modules/ble/build.sh
	fi

	# Hello World Module
	install -d ${D}${libdir}/azureiot/modules/hello_world
	install -m 0755 ${B}/modules/hello_world/libhello_world.so ${D}${libdir}/azureiot/modules/hello_world/

	install -d ${D}${includedir}/azureiot/modules/hello_world
	install -m 0644 ${S}/modules/hello_world/inc/*.h ${D}${includedir}/azureiot/modules/hello_world

	install -d ${D}${exec_prefix}/src/azureiotedge/modules/hello_world/src
	install -d ${D}${exec_prefix}/src/azureiotedge/modules/hello_world/inc
	install -m 0644 ${S}/modules/hello_world/src/*.c ${D}${exec_prefix}/src/azureiotedge/modules/hello_world/src/
	install -m 0644 ${S}/modules/hello_world/inc/*.h ${D}${exec_prefix}/src/azureiotedge/modules/hello_world/inc/
	install -m 0755 ${WORKDIR}/hello-world-module.sh ${D}${exec_prefix}/src/azureiotedge/modules/hello_world/build.sh

	# Identity Map Module
	install -d ${D}${libdir}/azureiot/modules/identitymap
	install -m 0755 ${B}/modules/identitymap/libidentity_map.so ${D}${libdir}/azureiot/modules/identitymap/
	
	install -d ${D}${includedir}/azureiot/modules/identitymap
	install -m 0644 ${S}/modules/identitymap/inc/*.h ${D}${includedir}/azureiot/modules/identitymap

	install -d ${D}${exec_prefix}/src/azureiotedge/modules/identitymap/src
	install -d ${D}${exec_prefix}/src/azureiotedge/modules/identitymap/inc
	install -m 0644 ${S}/modules/identitymap/src/*.c ${D}${exec_prefix}/src/azureiotedge/modules/identitymap/src/
	install -m 0644 ${S}/modules/identitymap/inc/*.h ${D}${exec_prefix}/src/azureiotedge/modules/identitymap/inc/
	install -m 0755 ${WORKDIR}/identitymap-module.sh ${D}${exec_prefix}/src/azureiotedge/modules/identitymap/build.sh

	# IoT Hub Module
	install -d ${D}${libdir}/azureiot/modules/iothub
	install -m 0755 ${B}/modules/iothub/libiothub.so ${D}${libdir}/azureiot/modules/iothub/

	install -d ${D}${includedir}/azureiot/modules/iothub
	install -m 0644 ${S}/modules/iothub/inc/*.h ${D}${includedir}/azureiot/modules/iothub

	install -d ${D}${exec_prefix}/src/azureiotedge/modules/iothub/src
	install -d ${D}${exec_prefix}/src/azureiotedge/modules/iothub/inc
	install -m 0644 ${S}/modules/iothub/src/*.c ${D}${exec_prefix}/src/azureiotedge/modules/iothub/src/
	install -m 0644 ${S}/modules/iothub/inc/*.h ${D}${exec_prefix}/src/azureiotedge/modules/iothub/inc/
	install -m 0755 ${WORKDIR}/iothub-module.sh ${D}${exec_prefix}/src/azureiotedge/modules/iothub/build.sh

	# Logger Module
	install -d ${D}${libdir}/azureiot/modules/logger
	install -m 0755 ${B}/modules/logger/liblogger.so ${D}${libdir}/azureiot/modules/logger/

	install -d ${D}${includedir}/azureiot/modules/logger
	install -m 0644 ${S}/modules/logger/inc/*.h ${D}${includedir}/azureiot/modules/logger

	install -d ${D}${exec_prefix}/src/azureiotedge/modules/logger/src
	install -d ${D}${exec_prefix}/src/azureiotedge/modules/logger/inc
	install -m 0644 ${S}/modules/logger/src/*.c ${D}${exec_prefix}/src/azureiotedge/modules/logger/src/
	install -m 0644 ${S}/modules/logger/inc/*.h ${D}${exec_prefix}/src/azureiotedge/modules/logger/inc/
	install -m 0755 ${WORKDIR}/logger-module.sh ${D}${exec_prefix}/src/azureiotedge/modules/logger/build.sh

	# Simulated Device Module
	install -d ${D}${libdir}/azureiot/modules/simulated_device
	install -m 0755 ${B}/modules/simulated_device/libsimulated_device.so ${D}${libdir}/azureiot/modules/simulated_device/

	install -d ${D}${includedir}/azureiot/modules/simulated_device
	install -m 0644 ${S}/modules/simulated_device/inc/*.h ${D}${includedir}/azureiot/modules/simulated_device

	install -d ${D}${exec_prefix}/src/azureiotedge/modules/simulated_device/src
	install -d ${D}${exec_prefix}/src/azureiotedge/modules/simulated_device/inc
	install -m 0644 ${S}/modules/simulated_device/src/*.c ${D}${exec_prefix}/src/azureiotedge/modules/simulated_device/src/
	install -m 0644 ${S}/modules/simulated_device/inc/*.h ${D}${exec_prefix}/src/azureiotedge/modules/simulated_device/inc/
	install -m 0755 ${WORKDIR}/simulated-device-module.sh ${D}${exec_prefix}/src/azureiotedge/modules/simulated_device/build.sh

	# Modbus Module
	install -d ${D}${libdir}/azureiot/modules/modbus_read
	install -m 0755 ${B}/modules/modbus_read/libmodbus_read.so ${D}${libdir}/azureiot/modules/modbus_read/

	install -d ${D}${includedir}/azureiot/modules/modbus_read
	install -m 0644 ${S}/modules/modbus_read/inc/*.h ${D}${includedir}/azureiot/modules/modbus_read

	# SQLite Module
	install -d ${D}${libdir}/azureiot/modules/sqlite
	install -m 0755 ${B}/modules/sqlite/libsqlite.so ${D}${libdir}/azureiot/modules/sqlite/

	install -d ${D}${includedir}/azureiot/modules/sqlite
	install -m 0644 ${S}/modules/sqlite/inc/*.h ${D}${includedir}/azureiot/modules/sqlite

	# Azure Functions Sample
	install -d ${D}${datadir}/azureiotedge/samples/azure_functions
	install -m 0755 ${B}/samples/azure_functions_sample/azure_functions_sample ${D}${datadir}/azureiotedge/samples/azure_functions/azure_functions
	install -m 0644 ${S}/samples/azure_functions_sample/src/azure_functions_lin.json ${D}${datadir}/azureiotedge/samples/azure_functions/azure_functions.json

	install -d ${D}${exec_prefix}/src/azureiotedge/samples/azure_functions/src
	install -m 0644 ${S}/samples/azure_functions_sample/src/*.c ${D}${exec_prefix}/src/azureiotedge/samples/azure_functions/src/
	install -m 0644 ${S}/samples/azure_functions_sample/src/azure_functions_lin.json ${D}${exec_prefix}/src/azureiotedge/samples/azure_functions/src/azure_functions.json
	install -m 0755 ${WORKDIR}/azure-functions-sample.sh ${D}${exec_prefix}/src/azureiotedge/samples/azure_functions/build.sh

	# BLE Gateway Sample
	if [ -e ${B}/samples/ble_gateway/ ]; then
		install -d ${D}${datadir}/azureiotedge/samples/ble_gateway
		install -m 0755 ${B}/samples/ble_gateway/ble_gateway ${D}${datadir}/azureiotedge/samples/ble_gateway/ble_gateway
		install -m 0644 ${B}/samples/ble_gateway/ble_printer/libble_printer.so ${D}${datadir}/azureiotedge/samples/ble_gateway/
		install -m 0644 ${S}/samples/ble_gateway/src/gateway_sample.json ${D}${datadir}/azureiotedge/samples/ble_gateway/ble_gateway.json

		install -d ${D}${exec_prefix}/src/azureiotedge/samples/ble_gateway/src
		install -d ${D}${exec_prefix}/src/azureiotedge/samples/ble_gateway/ble_printer/src
		install -d ${D}${exec_prefix}/src/azureiotedge/samples/ble_gateway/ble_printer/inc
		install -m 0644 ${S}/samples/ble_gateway/src/*.c ${D}${exec_prefix}/src/azureiotedge/samples/ble_gateway/src/
		install -m 0644 ${S}/samples/ble_gateway/src/gateway_sample.json ${D}${exec_prefix}/src/azureiotedge/samples/ble_gateway/src/gateway.json
		install -m 0644 ${S}/samples/ble_gateway/ble_printer/src/*.c ${D}${exec_prefix}/src/azureiotedge/samples/ble_gateway/ble_printer/src/
		install -m 0644 ${S}/samples/ble_gateway/ble_printer/inc/*.h ${D}${exec_prefix}/src/azureiotedge/samples/ble_gateway/ble_printer/inc/
		install -m 0755 ${WORKDIR}/ble-gateway-sample.sh ${D}${exec_prefix}/src/azureiotedge/samples/ble_gateway/build.sh
	fi

	# Dynamically Add Module Sample
	install -d ${D}${datadir}/azureiotedge/samples/dynamically_add_module
	install -m 0755 ${B}/samples/dynamically_add_module_sample/dynamically_add_module_sample ${D}${datadir}/azureiotedge/samples/dynamically_add_module/dynamically_add_module
	install -m 0644 ${S}/samples/dynamically_add_module_sample/src/links_lin.json ${D}${datadir}/azureiotedge/samples/dynamically_add_module/links.json
	install -m 0644 ${S}/samples/dynamically_add_module_sample/src/modules_lin.json ${D}${datadir}/azureiotedge/samples/dynamically_add_module/modules.json

	install -d ${D}${exec_prefix}/src/azureiotedge/samples/dynamically_add_module/src
	install -m 0644 ${S}/samples/dynamically_add_module_sample/src/*.c ${D}${exec_prefix}/src/azureiotedge/samples/dynamically_add_module/src/
	install -m 0644 ${S}/samples/dynamically_add_module_sample/src/links_lin.json ${D}${exec_prefix}/src/azureiotedge/samples/dynamically_add_module/src/links.json
	install -m 0644 ${S}/samples/dynamically_add_module_sample/src/modules_lin.json ${D}${exec_prefix}/src/azureiotedge/samples/dynamically_add_module/src/modules.json
	install -m 0755 ${WORKDIR}/dynamically-add-module-sample.sh ${D}${exec_prefix}/src/azureiotedge/samples/dynamically_add_module/build.sh

	# Hello World Sample
	install -d ${D}${datadir}/azureiotedge/samples/hello_world
	install -m 0755 ${B}/samples/hello_world/hello_world_sample ${D}${datadir}/azureiotedge/samples/hello_world/hello_world
	install -m 0644 ${S}/samples/hello_world/src/hello_world_lin.json ${D}${datadir}/azureiotedge/samples/hello_world/hello_world.json

	install -d ${D}${exec_prefix}/src/azureiotedge/samples/hello_world/src
	install -m 0644 ${S}/samples/hello_world/src/*.c ${D}${exec_prefix}/src/azureiotedge/samples/hello_world/src/
	install -m 0644 ${S}/samples/hello_world/src/hello_world_lin.json ${D}${exec_prefix}/src/azureiotedge/samples/hello_world/src/hello_world.json
	install -m 0755 ${WORKDIR}/hello-world-sample.sh ${D}${exec_prefix}/src/azureiotedge/samples/hello_world/build.sh

	# Native Module Host Sample
	install -d ${D}${datadir}/azureiotedge/samples/native_module_host
	install -m 0755 ${B}/samples/native_module_host_sample/native_host_sample ${D}${datadir}/azureiotedge/samples/native_module_host/native_host
	install -m 0755 ${B}/samples/native_module_host_sample/native_gateway ${D}${datadir}/azureiotedge/samples/native_module_host/native_gateway
	install -m 0644 ${S}/samples/native_module_host_sample/src/native_host_sample_lin.json ${D}${datadir}/azureiotedge/samples/native_module_host/native_host.json

	install -d ${D}${exec_prefix}/src/azureiotedge/samples/native_module_host/src
	install -m 0644 ${S}/samples/native_module_host_sample/src/*.c ${D}${exec_prefix}/src/azureiotedge/samples/native_module_host/src/
	install -m 0644 ${S}/samples/native_module_host_sample/src/native_host_sample_lin.json ${D}${exec_prefix}/src/azureiotedge/samples/native_module_host/src/native_host.json
	install -m 0755 ${WORKDIR}/native-module-host-sample.sh ${D}${exec_prefix}/src/azureiotedge/samples/native_module_host/build.sh

	# Proxy Sample
	install -d ${D}${datadir}/azureiotedge/samples/proxy
	install -m 0755 ${B}/samples/proxy_sample/proxy_sample ${D}${datadir}/azureiotedge/samples/proxy/proxy
	install -m 0755 ${B}/samples/proxy_sample/proxy_sample_remote ${D}${datadir}/azureiotedge/samples/proxy/proxy_remote
	install -m 0644 ${S}/samples/proxy_sample/src/proxy_sample_lin.json ${D}${datadir}/azureiotedge/samples/proxy/proxy.json

	install -d ${D}${exec_prefix}/src/azureiotedge/samples/proxy/src
	install -m 0644 ${S}/samples/proxy_sample/src/*.c ${D}${exec_prefix}/src/azureiotedge/samples/proxy/src/
	install -m 0644 ${S}/samples/proxy_sample/src/proxy_sample_lin.json ${D}${exec_prefix}/src/azureiotedge/samples/proxy/src/proxy.json
	install -m 0755 ${WORKDIR}/proxy-sample.sh ${D}${exec_prefix}/src/azureiotedge/samples/proxy/build.sh

	# Simulated Device Cloud Upload Sample
	install -d ${D}${datadir}/azureiotedge/samples/simulated_device_cloud_upload
	install -m 0755 ${B}/samples/simulated_device_cloud_upload/simulated_device_cloud_upload_sample ${D}${datadir}/azureiotedge/samples/simulated_device_cloud_upload/simulated_device_cloud_upload
	install -m 0644 ${S}/samples/simulated_device_cloud_upload/src/simulated_device_cloud_upload_lin.json ${D}${datadir}/azureiotedge/samples/simulated_device_cloud_upload/simulated_device_cloud_upload.json

	install -d ${D}${exec_prefix}/src/azureiotedge/samples/simulated_device_cloud_upload/src
	install -d ${D}${exec_prefix}/src/azureiotedge/samples/simulated_device_cloud_upload/inc
	install -m 0644 ${S}/samples/simulated_device_cloud_upload/src/*.c ${D}${exec_prefix}/src/azureiotedge/samples/simulated_device_cloud_upload/src/
	install -m 0644 ${S}/samples/simulated_device_cloud_upload/inc/*.h ${D}${exec_prefix}/src/azureiotedge/samples/simulated_device_cloud_upload/inc/
	install -m 0644 ${S}/samples/simulated_device_cloud_upload/src/simulated_device_cloud_upload_lin.json ${D}${exec_prefix}/src/azureiotedge/samples/simulated_device_cloud_upload/src/simulated_device_cloud_upload.json
	install -m 0755 ${WORKDIR}/simulated-device-cloud-upload-sample.sh ${D}${exec_prefix}/src/azureiotedge/samples/simulated_device_cloud_upload/build.sh

	# Modbus Sample
	install -d ${D}${datadir}/azureiotedge/samples/modbus
	install -m 0755 ${B}/samples/modbus_sample/modbus_sample ${D}${datadir}/azureiotedge/samples/modbus/modbus
	install -m 0644 ${S}/samples/modbus_sample/src/modbus_lin.json ${D}${datadir}/azureiotedge/samples/modbus/modbus.json

	install -d ${D}${exec_prefix}/src/azureiotedge/samples/modbus/src
	install -m 0644 ${S}/samples/modbus_sample/src/*.c ${D}${exec_prefix}/src/azureiotedge/samples/modbus/src/
	install -m 0644 ${S}/samples/modbus_sample/src/modbus_lin.json ${D}${exec_prefix}/src/azureiotedge/samples/modbus/src/modbus.json
	install -m 0755 ${WORKDIR}/modbus-sample.sh ${D}${exec_prefix}/src/azureiotedge/samples/modbus/build.sh

	# SQLite Sample
	install -d ${D}${datadir}/azureiotedge/samples/sqlite
	install -m 0755 ${B}/samples/sqlite_sample/sqlite_sample ${D}${datadir}/azureiotedge/samples/sqlite/sqlite
	install -m 0644 ${S}/samples/sqlite_sample/src/sqlite_lin.json ${D}${datadir}/azureiotedge/samples/sqlite/sqlite.json

	install -d ${D}${exec_prefix}/src/azureiotedge/samples/sqlite/src
	install -m 0644 ${S}/samples/sqlite_sample/src/*.c ${D}${exec_prefix}/src/azureiotedge/samples/sqlite/src/
	install -m 0644 ${S}/samples/sqlite_sample/src/sqlite_lin.json ${D}${exec_prefix}/src/azureiotedge/samples/sqlite/src/sqlite.json
	install -m 0755 ${WORKDIR}/sqlite-sample.sh ${D}${exec_prefix}/src/azureiotedge/samples/sqlite/build.sh

	# Java Binding
	if [ -e ${JAVA_LIB_DIR} ]; then
		install -d ${D}${libdir}/azureiot/bindings/java
    		install -m 0755 ${JAVA_LIB_DIR}/libjava_module_host.so ${D}${libdir}/azureiot/bindings/java/
	fi

	# Node.JS Binding
	if [ -e ${NODE_LIB_DIR} ]; then
		install -d ${D}${libdir}/azureiot/bindings/nodejs
    		install -m 0755 ${NODE_LIB_DIR}/libnodejs_binding.so ${D}${libdir}/azureiot/bindings/nodejs/
	fi

	# .NET Core Binding
	if [ -e ${DOTNET_LIB_DIR} ]; then
		install -d ${D}${libdir}/azureiot/bindings/dotnetcore
    		install -m 0755 ${DOTNET_LIB_DIR}/libdotnetcore.so ${D}${libdir}/azureiot/bindings/dotnetcore/
	fi
}

RDEPENDS_${PN} = "\
	glib-2.0 \
	curl \
"
FILES_${PN} += "\
	${libdir}/*.so \
"

RDEPENDS_${PN}-dev += "\
	azure-iot-sdk-c-dev \
	nanomsg-dev \
	glib-2.0-dev \
"
FILES_${PN}-dev += "\
	${includedir}/azureiot \
"

FILES_${PN}-dbg += "\
	${libdir}/azureiot/bindings/dotnetcore/.debug \
	${libdir}/azureiot/bindings/java/.debug \
	${libdir}/azureiot/bindings/nodejs/.debug \
	${libdir}/azureiot/modules/azure_functions/.debug \
	${libdir}/azureiot/modules/ble/.debug \
	${libdir}/azureiot/modules/hello_world/.debug \
	${libdir}/azureiot/modules/identitymap/.debug \
	${libdir}/azureiot/modules/iothub/.debug \
	${libdir}/azureiot/modules/logger/.debug \
	${libdir}/azureiot/modules/simulated_device/.debug \
	${libdir}/azureiot/modules/modbus_read/.debug \
	${libdir}/azureiot/modules/sqlite/.debug \
	${datadir}/azureiotedge/samples/azure_functions/.debug \
	${datadir}/azureiotedge/samples/ble_gateway/.debug \
	${datadir}/azureiotedge/samples/dynamically_add_module/.debug \
	${datadir}/azureiotedge/samples/proxy/.debug \
	${datadir}/azureiotedge/samples/hello_world/.debug \
	${datadir}/azureiotedge/samples/native_module_host/.debug \
	${datadir}/azureiotedge/samples/simulated_device_cloud_upload/.debug \
	${datadir}/azureiotedge/samples/modbus/.debug \
	${datadir}/azureiotedge/samples/sqlite/.debug \
"

FILES_${PN}-modules = "\
	${libdir}/azureiot/modules/azure_functions/libazure_functions.so \
	${libdir}/azureiot/modules/ble/libble.so \
	${libdir}/azureiot/modules/ble/libble_c2d.so \
	${libdir}/azureiot/modules/hello_world/libhello_world.so \
	${libdir}/azureiot/modules/identitymap/libidentity_map.so \
	${libdir}/azureiot/modules/iothub/libiothub.so \
	${libdir}/azureiot/modules/logger/liblogger.so \
	${libdir}/azureiot/modules/simulated_device/libsimulated_device.so \
"

FILES_${PN}-modules-src = "\
	${exec_prefix}/src/azureiotedge/modules \
"

RDEPENDS_${PN}-samples += "azure-iot-edge-modules"
FILES_${PN}-samples = "\
	${datadir}/azureiotedge/samples/azure_functions/* \
	${datadir}/azureiotedge/samples/ble_gateway/* \
	${datadir}/azureiotedge/samples/dynamically_add_module/* \
	${datadir}/azureiotedge/samples/hello_world/* \
	${datadir}/azureiotedge/samples/native_module_host/* \
	${datadir}/azureiotedge/samples/proxy/* \
	${datadir}/azureiotedge/samples/simulated_device_cloud_upload/* \
"

FILES_${PN}-samples-src = "\
	${exec_prefix}/src/azureiotedge/samples/azure_functions \
	${exec_prefix}/src/azureiotedge/samples/ble_gateway \
	${exec_prefix}/src/azureiotedge/samples/dynamically_add_module \
	${exec_prefix}/src/azureiotedge/samples/hello_world \
	${exec_prefix}/src/azureiotedge/samples/native_module_host \
	${exec_prefix}/src/azureiotedge/samples/proxy \
	${exec_prefix}/src/azureiotedge/samples/simulated_device_cloud_upload \
"

FILES_${PN}-module-modbus = "\
	${libdir}/azureiot/modules/modbus_read/*.so \
"

RDEPENDS_${PN}-samples-modbus += "\
	azure-iot-edge-modules \
	azure-iot-edge-module-modbus \
"
FILES_${PN}-samples-modbus = "\
	${datadir}/azureiotedge/samples/modbus/* \
"

FILES_${PN}-samples-src-modbus = "\
	${exec_prefix}/src/azureiotedge/samples/modbus \
"

FILES_${PN}-module-sqlite = "\
	${libdir}/azureiot/modules/sqlite/*.so \
"

RDEPENDS_${PN}-samples-sqlite += "\
	azure-iot-edge-modules \
	azure-iot-edge-module-sqlite \
	azure-iot-edge-module-modbus \
"
FILES_${PN}-samples-sqlite = "\
	${datadir}/azureiotedge/samples/sqlite/* \
"

FILES_${PN}-samples-src-sqlite = "\
	${exec_prefix}/src/azureiotedge/samples/sqlite \
"

FILES_${PN}-dotnetcore = "\
	${libdir}/azureiot/bindings/dotnetcore/*.so \
"

FILES_${PN}-java = "\
	${libdir}/azureiot/bindings/java/*.so \
"

FILES_${PN}-nodejs = "\
	${libdir}/azureiot/bindings/nodejs/*.so \
"

RRECOMMENDS_azure-iot-edge-dev[nodeprrecs] = "1"

INSANE_SKIP_${PN} += "rpaths"
INSANE_SKIP_${PN}-modules += "rpaths"
INSANE_SKIP_${PN}-module-modbus += "rpaths"
INSANE_SKIP_${PN}-module-sqlite += "rpaths"
INSANE_SKIP_${PN}-samples += "rpaths libdir"
INSANE_SKIP_${PN}-samples-modbus += "rpaths"
INSANE_SKIP_${PN}-samples-sqlite += "rpaths"
INSANE_SKIP_${PN}-dotnetcore += "rpaths"
INSANE_SKIP_${PN}-java += "rpaths"
INSANE_SKIP_${PN}-nodejs += "rpaths"
