# II-4-6 Multithreaded Sorting Application: Manual

**Сургууль:** Шинжлэх Ухаан Технологийн Их Сургууль, Мэдээлэл, Холбооны Технологийн Сургууль  
**Хичээл:** F.CSM302 Үйлдлийн систем, Бие даалт 1 (2026–2027 оны хичээлийн жилийн намар)  
**Сэдэв:** Multithreaded Sorting Application, Sorting animation  
**Хичээл заасан багш:** Г. Ганбат /F.CS10/  
**Хуваарилалтын код:** II-4-6  
**Гүйцэтгэсэн оюутнууд:** Б. Отгондалай /B241940132/, М. Мөнхгэрэлт /B232270012/, А. Базарсад /B242270065/, Б. Хаш-Эрдэнэ /B241930006/, Э. Мичидмаа /B221960015/, Ж. Суварнабазар /B251940118/

PDF хувилбар: [manual/manual.pdf](manual/manual.pdf)

## Агуулга

- [8.1 Хэрэглэгчийн заавар](#81-хэрэглэгчийн-заавар)
- [8.2 Operational заавар](#82-operational-заавар)

---

## 8.1 Хэрэглэгчийн заавар

### Програмын зорилго

Бүхэл тоон жагсаалтыг гурван бодит Java thread-ээр эрэмбэлнэ. Parent thread жагсаалтыг `[0, mid)` ба `[mid, n)` хоёр хэсэгт хуваана (mid = ⌈n/2⌉). Хоёр sorting thread (**Sorter-L**, **Sorter-R**) өөр өөрийн хэсгийг зэрэг эрэмбэлнэ. Дараа нь **Merger** thread хоёр хэсгийг хоёр дахь global массив `result[]` руу нэгтгэнэ. Parent бүх thread-ийг `join()` хийгээд үр дүнг хэвлэнэ. Web UI нь бодит thread-ийн үйлдэл бүрийг баганан animation-аар харуулна.

### Дэмжих орчин

- **JDK 17** буюу түүнээс дээш (`javac` хэрэгтэй, зөвхөн JRE хангалтгүй).
- macOS, Linux эсвэл Windows. Web UI-д Chrome, Edge, Firefox, Safari-ийн сүүлийн хувилбар.
- Гадны сан, Maven, Gradle, интернэт холболт шаардлагагүй.

### Суулгах / нээх алхам

- JDK 17 суулгана: macOS дээр `brew install --cask temurin@17`, Ubuntu дээр `sudo apt install openjdk-17-jdk`, Windows дээр https://adoptium.net-аас Temurin 17.
- Repository-г clone хийж эсвэл ZIP-ийг задлаад төслийн хавтас руу Terminal-аар орно.
- `javac -version` командаар `javac 17.x` гарч байгааг шалгана.
- `./build.sh` (Windows: `build.bat`) ажиллуулна. `Build OK (javac 17) -> out/` гарвал бэлэн.
- `./run.sh --web` ажиллуулж, хөтөч дээр http://localhost:8080 хаягийг нээнэ.

### Оролт ба тохиргоо

- **Тоонууд:** бүхэл тоонуудыг таслал эсвэл зайгаар тусгаарлана. Жишээ: `7, 12, 19, 3, 18`. Сөрөг тоо, давхардсан утга, сондгой n зөвшөөрнө.
- **Гараар / Санамсаргүй:** санамсаргүй үед n, Seed, Min, Max, Хэлбэр (random, sorted, reversed, duplicates)-ийг тохируулна.
- **Алгоритм:** Sorter-L ба Sorter-R тус бүрт insertion, bubble, selection, merge, quick-ээс сонгоно.
- **Горим:** **Replay**: Java дээр thread-үүд ажиллаж дуусаад, бүртгэсэн event-ийг урагш/хойш тоглуулна (n ≤ 300). **Lockstep**: thread-үүд амьд байж, алхам бүрд Semaphore-ийн permit хүлээнэ (n ≤ 100).
- **CLI:** `./run.sh --input "7,12,19,3"`, `--file PATH`, `--random N --seed S`, `--algo-left NAME --algo-right NAME`, `--help`.

### Ажиллуулах, зогсоох, reset хийх

| Товч | Үүрэг |
|---|---|
| Ажиллуулах | Оролтыг шалгаж, Java дээр шинэ run эхлүүлнэ |
| Тоглуулах / Түр зогсоох | Animation-ийг тасралтгүй тоглуулах эсвэл зогсооно (Space) |
| ◂ Алхам / Алхам ▸ | Нэг алхам ухрах (зөвхөн Replay) эсвэл урагшлах (← / →) |
| Эхнээс нь | Replay: эхлэл рүү буцна. Lockstep: шинэ session эхлүүлнэ |
| Хурд | 1–5000 алхам/сек |
| Байрлал | Гулсуураар дурын алхам руу шууд очно (зөвхөн Replay) |

Горимыг солиход дүрслэл цэвэрлэгдэнэ. Серверийг зогсоохдоо terminal дээр `Ctrl+C` дарна.

### Дүрслэлийн тэмдэг, өнгө, төлөв

| Өнгө | Утга |
|---|---|
| 🟦 Цэнхэр | Sorter-L-ийн хэсэг `[0, mid)` |
| 🟧 Улбар шар | Sorter-R-ийн хэсэг `[mid, n)` |
| 🟩 Ногоон | Merger-ийн `result[]`-д бичсэн утга |
| 🟥 Ягаан улаан | Яг одоо харьцуулж буй элементүүд |
| ⬜ Цагаан | Яг одоо сольсон эсвэл бичсэн элемент |
| ⬛ Бараан саарал | Merger аль хэдийн авсан элемент |
| Босоо зураас | Хуваалтын цэг `mid` |

Thread-ийн карт дээр төлөв гарна: *Үүсээгүй → start() дуудагдсан → Ажиллаж байна → Дууссан*, Parent-д *join() дээр хүлээж байна*. Lockstep горимд Java-гийн бодит төлөв (`NEW`, `RUNNABLE`, `WAITING`, `TERMINATED`) нэмж харагдана.

### Хоёроос доошгүй жишээ

**Жишээ 1 — Номын жишээ (Web UI, Replay)**

- “Номын жишээ” товч: `7, 12, 19, 3, 18, 4, 2, 6, 15, 8`. Sorter-L = insertion, Sorter-R = merge.
- “Ажиллуулах”, дараа нь “Алхам ▸” дарж хоёр хэсэг эрэмбэлэгдэж, Merger нэгтгэхийг ажиглана.
- Хүлээгдэх үр дүн: `[2, 3, 4, 6, 7, 8, 12, 15, 18, 19]`, Шалгалт: “Эрэмбэлэгдсэн, зөв”.

**Жишээ 2 — Сондгой n, өөр алгоритм (CLI)**

- `./run.sh --file examples/odd-11.txt --algo-left bubble --algo-right quick`
- Хуваалт: зүүн 6, баруун 5 элемент. Хүлээгдэх үр дүн: `[1, 2, …, 11]`, ЗӨВ.

**Жишээ 3 — Lockstep горим**

- Горим = Lockstep, “Ажиллуулах” дарна. Sorter-уудын карт дээр `WAITING` гарна.
- “Алхам ▸” бүрд нэг thread яг нэг үйлдэл хийнэ, хоёр sorter ээлжилнэ. Төгсгөлд бүгд `TERMINATED`.

### Түгээмэл алдаа ба шийдэл

| Алдаа / нөхцөл | Шийдэл |
|---|---|
| Хоосон оролт | Дор хаяж нэг бүхэл тоо оруулна |
| Тоо биш утга (`abc`) эсвэл бутархай (`2.5`) | Зөвхөн бүхэл тоо оруулна |
| int мужаас хэтэрсэн (`3000000000`) | −2³¹ … 2³¹−1 мужид багтах тоо |
| O(n²) алгоритмд n > 20 000 | merge/quick сонгох эсвэл `--force` |
| Web UI-д n > 300 (Lockstep: n > 100) | n-ийг багасгана |
| `javac олдсонгүй` / `JDK 17+ хэрэгтэй` | JDK 17 суулгаж, `JAVA_HOME`, PATH-ийг шалгана |
| `8080 порт аль хэдийн ашиглагдаж байна` | `./run.sh --web --port 8100` |
| `Сервертэй холбогдож чадсангүй` | `./run.sh --web`-ийг дахин ажиллуулна |

---

## 8.2 Operational заавар

### Repository clone хийх

```
git clone https://github.com/Munkhgerelt/F.CSM302-TeamProject-2-4-6.git
cd F.CSM302-TeamProject-2-4-6
```

### OS, compiler/runtime, хувилбар

JDK 17 буюу түүнээс дээш (`javac -version`); `build.sh` нь `javac --release 17` ашигладаг. Туршсан орчин: macOS 27 (Apple Silicon) + Temurin 17.0.21, Ubuntu 24.04 (amd64) + OpenJDK 21.

### Dependency суулгах

Гадны dependency байхгүй: зөвхөн JDK хэрэгтэй. Web сервер нь JDK-д багтсан `com.sun.net.httpserver`.

### Build ба Run команд

```
./build.sh && ./run.sh --file examples/book-example.txt
./run.sh --web
```

Windows: `build.bat`, `run.bat --web`. Скрипт ажиллахгүй бол `chmod +x *.sh`. Хөрвүүлсэн файлууд `out/`-д гарна (commit хийгдэхгүй).

### Үндсэн тестийн өгөгдөл

`examples/` хавтас: `book-example.txt` (номын жишээ), `odd-11.txt`, `duplicates.txt`, `reversed.txt`, `int-limits.txt`, `single.txt`; алдаатай: `invalid-letters.txt`, `invalid-overflow.txt`, `empty.txt`. Автомат тест: `./test.sh` (Windows: `test.bat`), JUnit-гүй 100 тест (`tests/TestRunner.java`). Хугацааны хэмжилт: `./bench.sh`.

### Нэмэлт boundary/error тест

- Нэг элемент: `./run.sh 42` → `[42]` (баруун хэсэг хоосон). Хоёр элемент: `./run.sh 2 1` → `[1, 2]`.
- Давхардсан: `--file examples/duplicates.txt` → `[1, 1, 1, 2, 2, 3, 3, 3, 3, 5, 5, 5]`.
- Хязгаар: `--file examples/int-limits.txt` → `-2147483648` ба `2147483647` зөв байрлана.
- Алдаатай оролт: `--input ""`, `--input "5,3,abc"`, `--input "1,2.5"` → алдааны мессеж, exit code 2.

### Хүлээгдсэн үр дүн

Номын жишээ: `[2, 3, 4, 6, 7, 8, 12, 15, 18, 19]`, `Шалгалт: ЗӨВ`, exit code 0. Автомат тест: `Нийт: 100, амжилттай: 100, алдаатай: 0`. Тест бүрийн оролт, хүлээгдсэн ба бодит утга [tests/results/test-results.md](../tests/results/test-results.md)-д, хэмжилт [tests/results/benchmark.md](../tests/results/benchmark.md)-д бичигдэнэ.

### Known issues

- Windows-ийн `.bat` скриптийг бодит Windows машин дээр туршаагүй.
- n бага үед хугацааны хэмжилт шуугиантай (JIT, thread үүсгэх зардал); trace-тэй ажиллалт удаан.
- Lockstep-д хойш алхах боломжгүй; Web сервер зөвхөн `127.0.0.1` дээр сонсоно.

### Нөөц ба эх сурвалж

- A. Silberschatz, P. B. Galvin, G. Gagne. *Operating System Concepts*, 10th ed., Chapter 4.
- Java SE 17 API: https://docs.oracle.com/en/java/javase/17/docs/api/
- Temurin JDK: https://adoptium.net

---

*Энэ гарын авлагын нооргийг Claude хиймэл оюун ухаан ашиглан бэлтгэсэн; багийн гишүүд уншиж шалгасан.*
