#!/bin/csh -f
#
# Concurrency test for getNextID.  Starts several workers that grab a
# bunch of objects concurrently.
#
# Test for success is not automated right now:  need to eyeball the
# conc* files to see whether they gave the expected results.

foreach i ( 0 1 2 3 )
    set out = "conc$i"
    rm -f "$out"
    ./run-test.sh TestH $i > "$out" &
end

wait

