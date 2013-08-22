@PLUGIN@ auto-degrade
=================

NAME
----
@PLUGIN@ auto-degrade - Enable automatic degrading

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ auto-degrade
```

DESCRIPTION
-----------
With automatic degrading, DEGRADED mode will be enabled when cluster membership
is detected to be more than one. Cluster membership is checked at the rate
specified by 'cluster.membershipQueryRate' in 'etc/multimaster.config', or every
5 seconds by default. A transition out of the DEGRADED state will be allowed
only once caches have been flushed.

ACCESS
------
Caller must be a member of the privileged 'Administrators' group.

SEE ALSO
--------

* [@PLUGIN@ degraded](cmd-degrade.html)