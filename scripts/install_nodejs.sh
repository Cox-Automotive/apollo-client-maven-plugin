#!/usr/bin/env bash

wget -qO- https://raw.githubusercontent.com/creationix/nvm/v0.34.0/install.sh | bash
# Activate nvm
export NVM_DIR=$HOME/.nvm
touch $HOME/.nvmrc
source $NVM_DIR/nvm.sh
# Use node 8.9
nvm install 8.9 && nvm alias default 8.9
echo 8.9 > $HOME/.nvmrc
# Enable nvm in following steps
echo "export NVM_DIR=$HOME/.nvm" >> $BASH_ENV
echo "source $NVM_DIR/nvm.sh" >> $BASH_ENV
# To fix npm install : "node-pre-gyp: Permission denied"
npm config set user 0
npm config set unsafe-perm true
node --version
npm --version