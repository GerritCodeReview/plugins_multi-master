@PLUGIN@ degrade
================

NAME
----
@PLUGIN@ degrade - Enable DEGRADED mode

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ degrade
```

DESCRIPTION
-----------
Enables DEGRADED mode in which certain caches are essentially disabled by being
flushed frequently so that peer coherency issues simply disappear at the cost of
efficiency.

ACCESS
------
Caller must be a member of the privileged 'Administrators' group.

SEE ALSO
--------

* [@PLUGIN@ auto-degrade](cmd-auto-degrade.html)