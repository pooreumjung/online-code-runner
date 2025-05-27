import sys
from collections import deque
input = sys.stdin.readline
N = int(input())
shark = []
for _ in range(N):
    a, b, c = map(int, input().split())
    shark.append((a,b,c))

edge = [[]for _ in range(2*N)]
for i in range(N):
    a, b, c = shark[i]
    for j in range(N):
        if i == j: continue
        ca, cb, cc = shark[j]
        if a == ca and b == cb and c == cc and i > j:
            continue
        if a >= ca and b >= cb and c >= cc:
            edge[i].append(j)
            edge[i+N].append(j)

pairL = [-1] * 2*N
pairR = [-1] * N
dist = [-1] * 2*N
INF = float('inf')

def bfs():
    check = False
    q = deque()
    for i in range(2*N):
        if pairL[i] == -1:
            dist[i] = 0
            q.append(i)
        else:
            dist[i] = INF
    while q:
        c = q.popleft()
        for i in edge[c]:
            if pairR[i] == -1:
                check = True
            elif dist[pairR[i]] == INF:
                dist[pairR[i]] = dist[c] + 1
                q.append(pairR[i])
    return check
            
def dfs(c):
    for i in edge[c]:
        if pairR[i] == -1 or (dist[pairR[i]] == dist[c] + 1 and dfs(pairR[i])):
            if pairL[c] != -1: pairR[pairL[c]] = -1
            pairL[c] = i
            pairR[i] = c
            return True
    dist[c] = INF
    return False
            
def process():
    matching = 0
    while bfs():
        for i in range(2*N):
            if pairL[i] == -1 and dfs(i):
                matching += 1
    return matching 

res=process()
print(N-res)