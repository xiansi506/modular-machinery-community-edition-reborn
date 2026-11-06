/* Whole-reload publication boundary. No world/packet/schema transforms.
 * Use structural descriptors for the completion field, so official/SRG names share one verified hook.
 */
function initializeCoreMod() {
    var O = Java.type('org.objectweb.asm.Opcodes');
    var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
    var Var = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var Field = Java.type('org.objectweb.asm.tree.FieldInsnNode');
    var H = 'com/reborn/modularmachinery/machine/ReloadCycle';
    var RM = 'Lnet/minecraft/server/packs/resources/ResourceManager;';
    var CF = 'Ljava/util/concurrent/CompletableFuture;';
    var EX = 'Ljava/util/concurrent/Executor;';
    var LIST = 'Ljava/util/List;';
    return {
        'mmcer_reload_owner': {
            target: { type: 'CLASS', name: 'net.minecraft.server.packs.resources.SimpleReloadInstance' },
            transformer: function(node) {
                var field = null;
                for (var i = 0; i < node.methods.size(); i++) {
                    var getter = node.methods.get(i);
                    if (getter.desc !== '()' + CF) continue;
                    for (var n = getter.instructions.getFirst(); n !== null; n = n.getNext()) {
                        if (n.getOpcode() === O.GETFIELD && n.owner === node.name && n.desc === CF) {
                            if (field !== null && field !== n.name) throw 'Ambiguous reload completion future';
                            field = n.name;
                        }
                    }
                }
                if (field === null) throw 'Missing reload completion future';
                var constructors = 0, begins = 0, ends = 0;
                for (var j = 0; j < node.methods.size(); j++) {
                    var m = node.methods.get(j);
                    if (m.name !== '<init>' || m.desc !== '(' + EX + EX + RM + LIST
                            + 'Lnet/minecraft/server/packs/resources/SimpleReloadInstance$StateFactory;' + CF + ')V') continue;
                    constructors++;
                    for (var ins = m.instructions.getFirst(); ins !== null; ) {
                        var next = ins.getNext();
                        if (ins.getOpcode() === O.INVOKESPECIAL && ins.owner === 'java/lang/Object' && ins.name === '<init>') {
                            var begin = new InsnList();
                            begin.add(new Var(O.ALOAD, 0));
                            begin.add(new Var(O.ALOAD, 3)); begin.add(new Var(O.ALOAD, 4));
                            begin.add(new Var(O.ALOAD, 2));
                            begin.add(new Call(O.INVOKESTATIC, H, 'begin', '(Ljava/lang/Object;' + RM + LIST + EX + ')' + LIST, false));
                            begin.add(new Var(O.ASTORE, 4));
                            m.instructions.insert(ins, begin); begins++;
                        }
                        if (ins.getOpcode() === O.RETURN) {
                            var end = new InsnList();
                            end.add(new Var(O.ALOAD, 0));
                            end.add(new Var(O.ALOAD, 0));
                            end.add(new Var(O.ALOAD, 0));
                            end.add(new Field(O.GETFIELD, node.name, field, CF));
                            // Original stack maps may mark the now-dead constructor arguments TOP.
                            // Only 'this' remains live here; the coordinator captured RM/executor at begin.
                            end.add(new Call(O.INVOKESTATIC, H, 'complete', '(Ljava/lang/Object;' + CF + ')' + CF, false));
                            end.add(new Field(O.PUTFIELD, node.name, field, CF));
                            m.instructions.insertBefore(ins, end); ends++;
                        }
                        ins = next;
                    }
                    m.maxStack = Math.max(m.maxStack, 4);
                }
                if (constructors !== 1 || begins !== 1 || ends !== 1)
                    throw 'Unexpected reload constructor structure: ' + constructors + '/' + begins + '/' + ends;
                return node;
            }
        }
    };
}
