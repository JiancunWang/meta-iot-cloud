inherit setuptools
require python-grpcio.inc

RDEPENDS_${PN} += "\
	${PYTHON_PN}-futures \
"