#!/usr/bin/env bash

REPDIR=`git rev-parse --show-toplevel`
HOOKDIR=$REPDIR/.git/hooks

# set-up post-merge hook
if [ ! -L $HOOKDIR/post-merge ]; then
	ln -s -f ../../.hooks/post-merge $HOOKDIR/post-merge
fi

# execute post-merge
.hooks/post-merge

