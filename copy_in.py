import sys
import shutil
import os
from pathlib import Path

def ccopy(rcfolder, relativefp):
    whichdir = Path("../{}".format(rcfolder)).resolve()
    src = whichdir.joinpath(relativefp)
    to = Path(".").resolve().joinpath(relativefp)
    Path(os.path.dirname(to)).mkdir(parents=True, exist_ok=True)
    shutil.copy(src, to)


if __name__ == '__main__':
    argv = sys.argv
    if len(argv) != 2:
        print("usage: {} repchain_folder_name".format(argv[0]))
        print("repchain folder should be placed in  ../repchain_folder_name")
    else:
        rcfolder = argv[1]
        ccopy(rcfolder, "conf/genesis.conf")
        ccopy(rcfolder, "src/main/scala/rep/sc/tpl/Sensors2Chain.scala")
        ccopy(rcfolder, "json/identity-net/genesis.json")
