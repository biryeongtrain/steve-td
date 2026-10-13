import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';

const [assets, indexFile, hexFile, outputDirectory, minecraft] = process.argv.slice(2);
if (!assets || !indexFile || !hexFile || !outputDirectory || !minecraft) {
    throw new Error('Usage: node generate.mjs <assets-directory> <asset-index-json> <extracted-hex-file> <output-directory> <minecraft-version>');
}
const assetRoot = path.resolve(assets) + path.sep;
const index = JSON.parse(fs.readFileSync(indexFile, 'utf8')).objects;
function asset(name) {
    const expected=index[name].hash;
    const data=fs.readFileSync(assetRoot+'objects/'+expected.slice(0,2)+'/'+expected);
    if(crypto.createHash('sha1').update(data).digest('hex')!==expected) throw Error('Asset hash mismatch');
    return data;
}
const metadata=JSON.parse(asset('minecraft/font/include/unifont.json'));
asset('minecraft/font/unifont.zip');
const overrides=metadata.providers.find(p=>p.hex_file==='minecraft:font/unifont.zip').size_overrides;
const ranges=[];
for(const line of fs.readFileSync(hexFile,'utf8').trim().split(/\r?\n/)) {
    const [id,hex]=line.split(':');
    const codePoint=parseInt(id,16),stride=hex.length/16;
    let union=0n;
    for(let row=0;row<16;row++)union|=BigInt('0x'+hex.slice(row*stride,(row+1)*stride));
    let left=0,right=-1;
    if(union!==0n){const bits=union.toString(2);left=stride*4-bits.length;right=stride*4-1-(bits.length-bits.replace(/0+$/,'').length);}
    const override=overrides.find(o=>codePoint>=o.from.codePointAt(0)&&codePoint<=o.to.codePointAt(0));
    if(override){left=override.left;right=override.right;}
    const advance=Math.floor((right-left+1)/2)+1;
    const last=ranges.at(-1);
    if(last&&last[1]+1===codePoint&&last[2]===advance)last[1]=codePoint;else ranges.push([codePoint,codePoint,advance]);
}
const data={minecraft,source:'https://resources.download.minecraft.net/'+index['minecraft/font/unifont.zip'].hash.slice(0,2)+'/'+index['minecraft/font/unifont.zip'].hash,sha1:index['minecraft/font/unifont.zip'].hash,providerSha1:index['minecraft/font/include/unifont.json'].hash,ranges};
const out=path.resolve(outputDirectory);fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(out+'/unifont-advances.json',JSON.stringify(data)+'\n');
console.log(JSON.stringify({ranges:ranges.length,bytes:fs.statSync(out+'/unifont-advances.json').size,samples:[0xAC00,0x2500].map(cp=>ranges.find(r=>r[0]<=cp&&cp<=r[1]))}));
