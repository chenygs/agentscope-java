import urllib.request, re, html

url = 'https://www.bing.com/search?q=2026%E5%B9%B4%E9%AB%98%E8%80%83%E4%BD%9C%E6%96%87%E9%A2%98%E7%9B%AE+%E5%85%A8%E5%9B%BD%E5%8D%B7'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'})
try:
    with urllib.request.urlopen(req, timeout=8) as resp:
        content = resp.read().decode('utf-8', errors='ignore')
        text = re.sub(r'<[^>]+>', ' ', content)
        text = html.unescape(text)
        text = re.sub(r'\s+', ' ', text)
        for line in text.split('。'):
            if any(kw in line for kw in ['高考', '作文', '题目', '2026', '全国卷', '新课标']):
                print(line.strip()[:400])
except Exception as e:
    print(f'Error: {e}')
