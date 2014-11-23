#!/usr/bin/env bash

REPDIR=`git rev-parse --show-toplevel`
HOOKDIR=$REPDIR/.git/hooks

# if [ -d $HOOKDIR ]; then
# 	mv $HOOKDIR $REPDIR/.git/hooks_old
# fi

# set-up post-merge hook
if [ ! -L $HOOKDIR/post-merge ]; then
	ln -s -f ../../git_hooks/post-merge $HOOKDIR/post-merge
fi

# execute post-merge
git_hooks/post-merge

