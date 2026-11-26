import test from 'node:test';
import assert from 'node:assert/strict';
import {TYPES,initialScene,newLayer,newComp,normalize,hydrate,isProp} from '../src/studio/scene.js';
import {vec,rgba,hexColor} from '../src/studio/renderer.js';
test('all layer types create valid property metadata',()=>{for(const t of TYPES){const l=newLayer(t);assert.equal(l.objType,t);assert.ok(Object.values(l).some(isProp));}});
test('scene roundtrip preserves unknown properties and nested controls',()=>{const s=initialScene();s.compositions.push(newComp());s.compositions[0].elements[1].unknown={t:'f 0 10',v:4,o:0,tag:'Test'};const r=hydrate(JSON.parse(JSON.stringify(s)));assert.equal(r.compositions[0].elements[1].unknown.v,4);assert.equal(r.compositions[0].elements[1].Segment1.mirror.v,1);assert.equal(r.compositions.length,2);});
test('invalid scene rejected',()=>{assert.throws(()=>normalize({compositions:[]}));assert.throws(()=>normalize({compositions:[{}]}));});
test('vector and ARGB helpers',()=>{assert.deepEqual(vec('0.5 -1'),[.5,-1]);assert.equal(hexColor(-65536),'#ff0000');assert.equal(rgba(-1),'rgba(255,255,255,1)');});
