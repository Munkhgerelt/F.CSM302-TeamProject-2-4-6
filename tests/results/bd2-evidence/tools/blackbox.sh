#!/usr/bin/env bash
# Black-box CLI + HTTP checks run identically against one build.
# Usage: blackbox.sh <project dir> <port>   -> prints "ID<TAB>result" lines
set -u
DIR="$1"; PORT="$2"
cd "$DIR" || exit 1
run() { # id, args...
  local id="$1"; shift
  local out; out=$(java -Dfile.encoding=UTF-8 -cp out sorting.Main "$@" 2>&1); local ec=$?
  local res; res=$(printf '%s\n' "$out" | grep -m1 -E '^Эрэмбэлсэн үр дүн:' | sed 's/^Эрэмбэлсэн үр дүн: //')
  local chk; chk=$(printf '%s\n' "$out" | grep -m1 -oE 'ЗӨВ|БУРУУ')
  local err; err=$(printf '%s\n' "$out" | grep -m1 -E '^Алдаа:' | perl -CSD -ne 'chomp; print substr($_,0,90)')
  local note; note=$(printf '%s\n' "$out" | grep -m1 -oE 'хоосон утга байсныг алгаслаа \([^)]*\)')
  printf '%s\texit=%s%s%s%s%s\n' "$id" "$ec" "${res:+; $res}" "${chk:+; $chk}" "${err:+; $err}" "${note:+; тэмдэглэл: $note}"
}
TMP=$(mktemp -d)
printf '# тайлбар\n7, 12 # мөрийн тайлбар\n19\n' > "$TMP/comments.txt"

run C01 --file examples/book-example.txt --algo-left insertion --algo-right merge
run C02 42
run C03 2 1
run C04 --file examples/odd-11.txt --algo-left bubble --algo-right quick
run C05 --file examples/duplicates.txt
run C06 --file examples/int-limits.txt
run C07 --input "-5,-5,0,0,9,-2147483648"
run C08 --file "$TMP/comments.txt"
run C09 --input ""
run C10 --input "5,3,abc"
run C11 --input "1,2.5"
run C12 --input "3000000000"
run C13 --random 25000 --algo insertion
run C14 --input "1,2" --algo foo
run C15 --random 0
run C16 --file examples/no-such-file.txt
run C17 --input "1,,2"
run C18 --input "1,2,"
run C19 --input ",5,3"
run C20 --input "1 2  3"
run C21 --input ",,"
s=$(date +%s%N); run C22 --random 1000000 --seed 1 --algo quick; e=$(date +%s%N)
printf 'C22t\t%d ms (process wall time)\n' $(( (e - s) / 1000000 ))
rm -rf "$TMP"

# ---------------- HTTP ----------------
java -cp out sorting.Main --web --port "$PORT" > /dev/null 2>&1 &
SRV=$!
for i in $(seq 1 50); do curl -s -o /dev/null "http://127.0.0.1:$PORT/" && break; sleep 0.1; done
B="http://127.0.0.1:$PORT"
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
body() { curl -s "$@"; }
printf 'H01\tGET / -> %s\n' "$(code $B/)"
printf 'H02\tGET /favicon.ico -> %s\n' "$(code $B/favicon.ico)"
printf 'H03\tindex.html icon зарласан: %s\n' "$(body $B/ | grep -c 'rel="icon"')"
r=$(body -X POST --data-urlencode 'input=7,12,19,3,18,4,2,6,15,8' -d 'source=input&algoLeft=insertion&algoRight=merge' $B/api/run)
printf 'H04\tномын жишээ: sorted=%s, eventCount=%s\n' "$(echo "$r" | grep -oE '"sorted":(true|false)' | head -1 | cut -d: -f2)" "$(echo "$r" | grep -oE '"eventCount":[0-9]+' | cut -d: -f2)"
h5c=$(code -X POST --data-urlencode "input=5,abc" -d source=input $B/api/run); h5m=$(body -X POST --data-urlencode "input=5,abc" -d source=input $B/api/run | grep -oE "2-р утга [^\"]*"); printf "H05\tinput=5,abc -> %s; %s\n" "$h5c" "$h5m"
printf 'H06\tReplay n=301 -> %s\n' "$(code -X POST -d 'source=random&n=301&seed=1&min=1&max=100&pattern=random' $B/api/run)"
printf 'H07\tGET /api/run -> %s\n' "$(code $B/api/run)"
printf 'H08\t/../../etc/passwd -> %s, %%2e%%2e -> %s\n' "$(code --path-as-is $B/../../etc/passwd)" "$(code --path-as-is "$B/%2e%2e/%2e%2e/etc/passwd")"
head -c 3000000 /dev/zero | tr '\0' 'a' > /tmp/bb_big_$$; printf 'H09\t3 MB body -> %s\n' "$(code -X POST --data-binary @/tmp/bb_big_$$ $B/api/run)"; rm -f /tmp/bb_big_$$
r=$(body -X POST --data-urlencode 'input=1,,2' -d 'source=input' $B/api/run)
printf 'H10\tinput=1,,2: sorted=%s, хоосон утгын тэмдэглэл=%s\n' "$(echo "$r" | grep -oE '"sorted":(true|false)' | head -1 | cut -d: -f2)" "$(echo "$r" | grep -c 'хоосон утга')"
printf 'H11\tLockstep n=101 -> %s\n' "$(code -X POST -d 'source=random&n=101&seed=1&min=1&max=100&pattern=random' $B/api/live/start)"
ids=(); fifth=""
for i in 1 2 3 4 5; do
  r=$(body -X POST --data-urlencode 'input=5,4,3,2,1' -d 'source=input' $B/api/live/start)
  ids+=("$(echo "$r" | grep -oE '"id":"s[0-9]+"' | cut -d'"' -f4)"); [ $i = 5 ] && fifth="$r"; sleep 0.03
done
printf 'H12\t5 дахь start-ийн хариунд хязгаарын тэмдэглэл: %s\n' "$(echo "$fifth" | grep -c 'хязгаар (4)')"
r=$(body "$B/api/live/state?id=${ids[0]}&from=0")
printf 'H13\tхаагдсан эхний session: %s\n' "$(echo "$r" | grep -oE '"error":"[^"]*' | perl -CSD -ne 'chomp; print substr($_,9,100)')"
alive=0; for i in 1 2 3 4; do [ "$(code "$B/api/live/state?id=${ids[$i]}&from=0")" = 200 ] && alive=$((alive+1)); done
printf 'H14\tажиллаж буй session (2–5 дахь): %s/4\n' "$alive"
printf 'H15\tstep count=0 -> %s\n' "$(code -X POST -d "id=${ids[1]}&count=0" $B/api/live/step)"
r=$(body -X POST -d "id=${ids[1]}&count=1&from=0" $B/api/live/step)
printf 'H16\tstep count=1: consumed=%s, waitingForPermit=%s\n' "$(echo "$r" | grep -oE '"consumed":[0-9]+' | cut -d: -f2)" "$(echo "$r" | grep -oE '"waitingForPermit":[0-9]+' | cut -d: -f2)"
r=$(body -X POST -d "id=${ids[1]}&count=500&from=0" $B/api/live/step)
printf 'H17\tstep count=500: finished=%s, TERMINATED=%s\n' "$(echo "$r" | grep -oE '"finished":(true|false)' | cut -d: -f2)" "$(echo "$r" | grep -o TERMINATED | wc -l | tr -d ' ')"
t=$(curl -s -o /dev/null -w '%{time_total}' -X POST -d 'source=random&n=300&seed=1&min=1&max=100&pattern=reversed&algoLeft=insertion&algoRight=insertion' $B/api/run)
printf 'H18t\tReplay n=300 insertion reversed хариу: %s s\n' "$t"
kill $SRV 2>/dev/null; wait $SRV 2>/dev/null
