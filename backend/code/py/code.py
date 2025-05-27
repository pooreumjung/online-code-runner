n,k=map(int,open(0))
l,r=1,k
while l<r:
    r,l=[m:=l+r>>1,r,l,m+1][sum(min(m//-~i,n)for i in range(n))<k::2]
print(l)