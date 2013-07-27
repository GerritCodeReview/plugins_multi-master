Multi-Master Configuration
==========================

This document describes how to setup multiple Gerrit master
servers using shared back-ends: shared DB and shared git
repos.  With multiple Gerrit masters it is possible to
mitigate server load by allowing users to access a server
which has more free resources, and it is also possible to
provide higher availability by allowing service to be
transferred to any remaining masters when a master fails.

Running a multi-master setup is inherently complicated and,
depending on the configuration and external hardware and
software available, it is possible to configure a multi-
master setup with very different levels of service.
Supported configuration options are outlined below.

Sharing Git Repositories
------------------------

A single site multi-master arrangement generally
consists of pointing all the masters to the same copy of
the git repositories via a shared file system.

### Simple Shared Host Test Setup ###

The simplest example of this is to host two gerrit servers
on the same host and to point the two servers at the same
directory where the git repos are located.  This is likely
only useful as an example for testing, since running two
gerrit servers on the same host is unlikely to provide any
additional load balancing or to improve availability very
much.  But this may be useful for those interested in
understanding how a multi-master setup works since it is
very easy to setup.

### Sharing via a Network File System ###

Running each master on a separate physical host provides a
load balancing improvement.  A shared file system such as
NFS is likely the best option for this arrangement,
although any JGit supported shared file system will work.

To share the git repos, initialize the Gerrit instances as
shown below:

```
  $ java -jar {path/}gerrit.war init -d <site1>
  # ...
  *** Git Repositories
  ***

  Location of Git repositories   [git]:  /path/to/nfs/git
  # choose any location on the network shared file system
    for git repository location.  NOTE: the location of the
    git repository must be shared by all servers.  If you
    use the default, [git], then the repository will be
    located on this server in 'site1/git'.  It is
    recommended that you avoid the default and specify the
    full path to the git repository, even if it is on this
    server.  The full path can then be used when setting up
    the other servers.
  # ...
```

```
  $ java -jar {path/}gerrit.war init -d <site2>
  # ...
  *** Git Repositories
  ***

  Location of Git repositories   [git]:  /path/to/nfs/git
  # choose the same git repository location that the first
    instance uses
  # ...
```

Sharing The Database
--------------------

All servers must use the same database to ensure users
have access to the same data.  Therefore PostgreSQL or MySQL
should be used as the database.  The default, embedded H2
db, cannot be used since multiple servers cannot connect to
the same H2 database in embedded mode.

To share the database, initialize the Gerrit instances as
shown below:

```
  $ java -jar {path/}gerrit.war init -d <site1>
  # ...
  *** SQL Database
  ***

  Database server type           [H2/?]:
  # choose anything except "h2" server type for database
  # choose database options, username, and password based
    on your setup
  # ...
```

```
  $ java -jar {path/}gerrit.war init -d <site2>
  # ...
  *** SQL Database
  ***

  Database server type           [H2/?]:
  # choose same options as for the first instance for sql
    database, since we want all instances to use the same
    database.
  # ...
```

Cache Coherency
---------------

Gerrit uses caches to speed up data access.  Each server
uses its own local cache which means data in these
caches is not shared with the other servers.  While
incoherent caches across servers will not result in data
consistency issues at the database or git layers (i.e. it
will not corrupt your data), it can result in many user
facing issues.

Generally speaking, the user facing issues result in an
unpleasant user experience, for example, new projects
created on one server do not show up on the other servers.
However, in some cases cache coherency issues may be
considered to have security implications.  For example,
there is a large delay before a group membership change on
one server shows up on the other servers.  This delay can
result in outdated ACLs being applied to certain users.

Some caches of note are:

* accounts: important details of an active user, including
their display name, preferences, known email addresses,
and group memberships
* projects: project description records
* groups_members: group membership information
* sshkeys: unpacked versions of user SSH keys, so the
internal SSH daemon can match against them during
authentication


### Brute Force Cache Coherency ###

The simplest solution to ensure cache coherency is to
disable the caches so each server is forced to get
up-to-date data from the database and repos on every
request.  Obviously this approach has a performance
impact on masters and should only be used if your
benefits of having multiple masters outweighs this
performance loss.

To disable the caches add the following lines to each
server's config, `<site>/etc/gerrit.config`:

```
  [cache "accounts"]
    memoryLimit = 0
    diskLimit = 0
  [cache "projects"]
    memoryLimit = 0
    diskLimit = 0
  [cache "groups_members"]
    memoryLimit = 0
    diskLimit = 0
  [cache "sshkeys"]
    memoryLimit = 0
    diskLimit = 0
```

Restart all servers for the config changes to take effect.

```
  $ ./<site1>/bin/gerrit.sh restart
  $ ./<site2>/bin/gerrit.sh restart
```

Web Sessions
------------

Web sessions are a special case of caches.  The web session
caches are authoritative and the sessions are not stored
anywhere else.  So with a standard gerrit it is not possible
to share web sessions across masters.

### Non-Shared Web Sessions ###

Since a web session is identified using the hostname,
it is possible to simply have different sessions for each
master server.  With such a setup, logging in or out of one
master does not log the user in or out of the other masters.
The masters simply appear as different sites to users (but
the back-end data is still the same).  Naturally, without
shared web sessions, no automatic load balancing, or fail
over is available for these sessions.

Non-shared web sessions does not provide an ideal solution;
users must manually switch between the servers to load
balance or fail over.  Mostly this does not provide a very
satisfying user experience, however, there are cases where
such a solution still provides an overall improvement over
using a single master.

One way to improve the experience is to point each user to
a per user master and attempting to split users evenly
across servers.  This may work well when there is a natural
split in your users:  perhaps to split users from a remote
site, or automation users off to their own server.  Such
splits might happen without a multi-master solution anyway
in many situations by using independent masters.  Lacking
an alternative it is worth asking if the non-shared web
sessions approach might be an improvement over independent
master.  See [rationale](#rationale) for other reasons why
you might want to use this setup.


HTTP Access
-----------

Each server must listen to a different http ip:port
combination.  In order to load balance or fail-over users
to different masters, they must get distributed across
masters.

### Separate Host URLs ###

If web sessions are not shared across masters, different
host URLs must be used for each master and http load
balancing and fail over must be done manually.  Using a
different host URL will distribute users based on the
initial URL they choose to access the master.  Subsequent
accesses will be to the same master.

While separate host URLs does not provide a great http
user experience, see [rationale](#rationale) for why you
might want to do this absent any other solutions.

```
  $ java -jar {path/}gerrit.war init -d <site1>
  # ...
  *** HTTP Daemon
  ***

  Listen on address              [*]: server1
  Listen on port                 [8080]: port1
```

```

  $ java -jar {path/}gerrit.war init -d <site2>
  # ...
  *** HTTP Daemon
  ***

  Listen on address              [*]: server2
  Listen on port                 [8080]: port2
  # choose a different <ip>:<port> combination for HTTP
    daemons than what the first instance uses (if this
    instance is on another server, you can still use the
    defaults)
  # ...
```

### Same Host URLs and Load Balancing ###

NOTE: Shared web sessions must be setup first.
Accessing masters on different servers using the same host
URL requires using a load balancer.  By connecting to the
masters through a load balancer, the users will see only one
hostname (the load balancer's), and thus will have just one
session.  Any standard load balancer can be used.

The load balancer's front-end http address should be made
different from that of any master.  Configure the load
balancer's back-end with the http addresses of all the
masters.  To have the masters direct clients to connect to
the load balancer's http address, add the following lines to
each master's config, `<site>/etc/gerrit.config`:

```
  [gerrit]
    canonicalWebUrl = http[s]://<ip>:<port>
                         # http address of the load balancer
```

Restart all servers for the config changes to take effect.

A sample setup using HAProxy is given below:

```
  global
    daemon
    pidfile /var/run/haproxy.pid

  defaults
    mode http
    timeout connect 5000ms
    timeout client 50000ms
    timeout server 50000ms

  frontend http-in
    bind <ip>:<http_port>
    # NOTE: users should connect over http to
      <ip>:<http_port>, which should be the same as the
      gerrit.canonicalWebUrl parameter in the
      'gerrit.config' files
    default_backend http-servers

  backend http-servers
    server server1 <server1_ip>:<server1_http_port>
    server server2 <server2_ip>:<server2_http_port>
```

See [Using HAProxy](#HAProxy) for how to start and stop HAProxy.


SSH Access
----------

As with http, each server must listen to a different ssh
ip:port combination.

```
  $ java -jar {path/}gerrit.war init -d <site1>
  # ...
  *** SSH Daemon
  ***

  Listen on address              [*]: server1
  Listen on port                 [29418]: port1
```

```
  $ java -jar {path/}gerrit.war init -d <site2>
  # ...
  *** SSH Daemon
  ***

  Listen on address              [*]: server2
  Listen on port                 [29418]: port2
  # choose a different <ip>:<port> combination for SSH
    daemons than what the first instance uses (if this
    instance is on another server, you can still use the
    defaults)
  # ...
```

To prevent users from getting a security warning when
connecting over ssh, have all masters use the same ssh-rsa
host key by copying '\<site\>/etc/ssh_host_rsa_key' and
'\<site\>/etc/ssh_host_rsa_key.pub' from any one master to
the others.

### Load Balancing ###

In order to load balance or fail-over users to different
masters they must be distributed across masters.  However,
since ssh sessions are not persistent across connections,
any standard ssh load balancer can be used to distribute ssh
connections across the available masters.

The load balancer's front-end ssh address should be made
different from that of any master.  Configure the load
balancer's back-end with the ssh addresses of all the
masters.  To have the masters direct clients to connect to
the load balancer's ssh address, add the following lines to
each master's config, `<site>/etc/gerrit.config`:

```
  [sshd]
    advertisedAddress = <ip>:<port>    # ssh address of the
                                         load balancer
```

Restart all servers for the config changes to take effect.

A sample <haproxy_config> file for HAProxy:

```
  global
    daemon
    pidfile /var/run/haproxy.pid

  defaults
    mode tcp
    retries 3
    timeout connect 5000ms
    timeout client 50000ms
    timeout server 50000ms

  frontend ssh-in
    bind <ip>:<ssh_port>
    # NOTE: users should connect over ssh to
    # <ip>:<ssh_port>, which should be the same as the
    # sshd.advertisedAddress parameter in the
    # 'gerrit.config' files
    default_backend ssh-servers

  backend ssh-servers
    option redispatch
    server server1 <server1_ip>:<server1_ssh_port> check
    server server2 <server2_ip>:<server2_ssh_port> check
```

See [Using HAProxy](#HAProxy) for how to start and stop HAProxy.


###<a id="rationale"> Rationale</a>

The different host URL setup is valuable if you mainly
care to load balance ssh traffic and don't care which http
master your users hit.  Gerrit http traffic is generally
very light compared to Gerrit ssh traffic (unless git over
http is used).  This can be used as a simple upgrade path
for slaves allowing them to be used as masters for ssh
data.  This is also useful for load balancing anonymous
git http traffic since it does not require a session.  If
you choose to use multi-masters only for ssh, you want to
set your canonical URL to point to the single http master
so that change upload messages created by each master
point to the correct http URL.

###<a id="HAProxy">Using HAProxy</a>

For more information go [here][http://haproxy.1wt.eu/].

Check if the HAProxy config file is valid:

```
  $ sudo haproxy -f <haproxy_config> -c
```

Start HAProxy:

```
  $ sudo haproxy -f <haproxy_config>
```

HAProxy can be stopped using "sudo kill \<haproxy_pid\>".
The HAProxy PID can be found using "ps -e | grep haproxy".
If you are using the example config file, the PID can also
be found in '/var/run/haproxy.pid'.  To reload a new
configuration with minimal service impact and without
breaking existing sessions, run:

```
  $ sudo haproxy -f haproxy.cfg -sf <haproxy_pid>
```
