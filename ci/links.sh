#!/usr/bin/env bash
# The trees the provider's link tests walk, made on the CI's server (nfsd's root export is
# /srv/nfs): "links" for the basics, and "sec" (the export "/sec" to the tests) for safety: links
# out of the export, loops, a chain, and what a plain user (uid 1234) may reach or not.
set -euo pipefail
r=/srv/nfs s=/srv/nfs/sec
link() { sudo ln -sfn "$1" "$2"; }
sudo mkdir -p $r/links $s/{data/sub,private,locked,noread/ok,victim,bag,tocopy,chain,links}
link ../fixtures/movie-a.bin $r/links/movie
link ../fixtures $r/links/fixtures
link ../../outside $r/links/outside
link loop $r/links/loop
for f in data/file.txt data/sub/inner.txt private/secret.txt readonly.txt victim/a victim/b; do
  echo hello | sudo tee "$s/$f" > /dev/null
done
sudo chmod 0777 $s/data $s/noread/ok
sudo chmod 0700 $s/private
sudo chmod 0600 $s/private/secret.txt
sudo chmod 0444 $s/readonly.txt
sudo chmod 0711 $s/noread
# Out of the export: absolute, relative, mixed, in a chain's third hop, a prefix that only looks
# like the export's, another export of the server.
link / $s/links/root
link /etc $s/links/etc
link /etc/passwd $s/links/passwd
link ../../../.. $s/links/escape
link ../data/../../.. $s/links/mixed
link ./../data/../../.. $s/links/dotted
link chain2 $s/links/chain1
link ../links/chain3 $s/links/chain2
link ../../../outside $s/links/chain3
link /sec-other/x $s/links/prefix
link ../../tls $s/links/other-export
# Inside it: absolute (under the export's path) and relative, to what root or anyone may reach.
link /sec/data $s/links/abs-inside
link ../data $s/links/data
link ../private $s/links/private
link ../private/secret.txt $s/links/secret
link ../readonly.txt $s/links/readonly
link ../noread/ok $s/links/through-noread
link ../victim $s/links/victim
# Loops, and links to their own folder or the one above.
link . $s/links/self
link .. $s/links/up
link loop $s/links/loop
link pb $s/links/pa
link pa $s/links/pb
# To delete one by one, to copy, and a chain of 41 links (40 is the most followed).
link ../data $s/bag/in
link ../../../x $s/bag/out
link loop $s/bag/loop
link ../data/file.txt $s/bag/file
link ../data $s/tocopy/in
link ../../../x $s/tocopy/out
for i in $(seq 0 40); do link "c$((i + 1))" "$s/chain/c$i"; done
link ../data/file.txt $s/chain/c41
# A link in a folder the plain user cannot write.
link ../data $s/locked/l
sudo chmod 0555 $s/locked
