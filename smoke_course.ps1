$base = 'http://127.0.0.1:8080/api'
function Login($u, $t) {
  $b = '{"username":"' + $u + '","password":"123456","loginType":"' + $t + '"}'
  $r = Invoke-RestMethod -Uri ($base + '/auth/login') -Method Post -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 30
  return $r.data.accessToken
}
$tk = Login 'T004' 'TEACHER'
$th = @{ Authorization = 'Bearer ' + $tk }
$mc = Invoke-RestMethod -Uri ($base + '/course-space/my-courses') -Headers $th
Write-Output ('TEACHER_COURSES=' + $mc.data.Count)
$offering = $mc.data[0].offeringId
Write-Output ('OFFERING=' + $offering)
$ch = Invoke-RestMethod -Uri ($base + "/course-space/$offering/chapters?title=第一章%20计算机基础&description=课程绪论") -Headers $th -Method Post
Write-Output ('CHAPTER=' + $ch.data.id)
$ck = Invoke-RestMethod -Uri ($base + "/checkin?offeringId=$offering&title=第1次课堂签到") -Headers $th -Method Post
$code = $ck.data.code
Write-Output ('CHECKIN_ID=' + $ck.data.id + ' CODE=' + $code)
$st = Login '2300000001' 'STUDENT'
$sh = @{ Authorization = 'Bearer ' + $st }
$smc = Invoke-RestMethod -Uri ($base + '/course-space/my-courses') -Headers $sh
Write-Output ('STU_COURSES=' + $smc.data.Count)
$sign = Invoke-RestMethod -Uri ($base + "/checkin/$($ck.data.id)/sign?code=$code") -Headers $sh -Method Post
Write-Output ('SIGN_OK=' + $sign.code)
try { Invoke-RestMethod -Uri ($base + "/checkin/$($ck.data.id)/sign?code=$code") -Headers $sh -Method Post -ErrorAction Stop | Out-Null; Write-Output 'DUP=UNEXPECTED' } catch { Write-Output ('DUP_REJECTED=' + $_.Exception.Response.StatusCode.value__) }
$stt = Invoke-RestMethod -Uri ($base + "/checkin/$($ck.data.id)/stats") -Headers $th
Write-Output ('STATS total=' + $stt.data.total + ' signed=' + $stt.data.signedCount)
$qs = '[{"no":"1","type":"CHOICE","content":"以下哪个是CPU？","answer":"A","score":20},{"no":"2","type":"FILL","content":"1+1=?","answer":"2","score":20},{"no":"3","type":"JUDGE","content":"地球是圆的","answer":"对","score":20}]'
$qb = '{"title":"第一章章节测验","courseId":1,"courseName":"程序设计基础","clazzId":2,"courseOfferingId":' + $offering + ',"questions":' + $qs + '}'
$qz = Invoke-RestMethod -Uri ($base + '/homework') -Method Post -Headers $th -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($qb))
Write-Output ('QUIZ=' + $qz.data)
$sql = Invoke-RestMethod -Uri ($base + '/homework/offering/mine') -Headers $sh
Write-Output ('STU_QUIZZES=' + $sql.data.Count)
$ab = '{"answers":[{"no":"1","answer":"A"},{"no":"2","answer":"2"},{"no":"3","answer":"对"}]}'
$sub = Invoke-RestMethod -Uri ($base + "/homework/$($qz.data)/submit") -Headers $sh -Method Post -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($ab))
Write-Output ('SUBMIT_OK=' + $sub.code)
$myl = Invoke-RestMethod -Uri ($base + '/homework/my') -Headers $sh
$mine = $myl.data | Where-Object { $_.id -eq $qz.data }
Write-Output ('MY_SCORE=' + $mine.score)
$d = Invoke-RestMethod -Uri ($base + "/course-space/$offering/discussions?content=大家好，有问题可以在这里提问") -Headers $th -Method Post
$did = $d.data.id
Invoke-RestMethod -Uri ($base + "/course-space/discussions/$did/reply?content=老师好") -Headers $sh -Method Post | Out-Null
Invoke-RestMethod -Uri ($base + "/course-space/discussions/$did/like") -Headers $sh -Method Post | Out-Null
$dl = Invoke-RestMethod -Uri ($base + "/course-space/$offering/discussions") -Headers $sh
Write-Output ('DISC replies=' + $dl.data[0].replyCount + ' likes=' + $dl.data[0].likeCount + ' pinned=' + $dl.data[0].pinned)
curl.exe -s -X POST -H ("Authorization: Bearer " + $tk) -F "file=@C:\Users\郭耀彬\Desktop\gybct\gybxx\server\testdata\sample_exam.docx" -F "chapterId=$($ch.data.id)" ($base + "/course-space/$offering/materials") | Out-Null
$ml = Invoke-RestMethod -Uri ($base + "/course-space/$offering/materials") -Headers $th
Write-Output ('MATERIALS=' + $ml.data.Count)