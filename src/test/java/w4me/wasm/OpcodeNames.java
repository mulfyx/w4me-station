package w4me.wasm;

/** Stable human-readable labels for decoded WebAssembly and W4IR profile output. */
final class OpcodeNames {
    private static final String[] STANDARD = new String[256];

    static {
        name(0x00, "unreachable");
        name(0x01, "nop");
        name(0x02, "block");
        name(0x03, "loop");
        name(0x04, "if");
        name(0x05, "else");
        name(0x0b, "end");
        name(0x0c, "br");
        name(0x0d, "br_if");
        name(0x0e, "br_table");
        name(0x0f, "return");
        name(0x10, "call");
        name(0x11, "call_indirect");
        name(0x1a, "drop");
        name(0x1b, "select");
        name(0x1c, "select_t");
        name(0x20, "local.get");
        name(0x21, "local.set");
        name(0x22, "local.tee");
        name(0x23, "global.get");
        name(0x24, "global.set");
        name(0x28, "i32.load");
        name(0x29, "i64.load");
        name(0x2a, "f32.load");
        name(0x2b, "f64.load");
        name(0x2c, "i32.load8_s");
        name(0x2d, "i32.load8_u");
        name(0x2e, "i32.load16_s");
        name(0x2f, "i32.load16_u");
        name(0x30, "i64.load8_s");
        name(0x31, "i64.load8_u");
        name(0x32, "i64.load16_s");
        name(0x33, "i64.load16_u");
        name(0x34, "i64.load32_s");
        name(0x35, "i64.load32_u");
        name(0x36, "i32.store");
        name(0x37, "i64.store");
        name(0x38, "f32.store");
        name(0x39, "f64.store");
        name(0x3a, "i32.store8");
        name(0x3b, "i32.store16");
        name(0x3c, "i64.store8");
        name(0x3d, "i64.store16");
        name(0x3e, "i64.store32");
        name(0x3f, "memory.size");
        name(0x40, "memory.grow");
        name(0x41, "i32.const");
        name(0x42, "i64.const");
        name(0x43, "f32.const");
        name(0x44, "f64.const");
        String[] comparisons = {
            "i32.eqz",
            "i32.eq",
            "i32.ne",
            "i32.lt_s",
            "i32.lt_u",
            "i32.gt_s",
            "i32.gt_u",
            "i32.le_s",
            "i32.le_u",
            "i32.ge_s",
            "i32.ge_u",
            "i64.eqz",
            "i64.eq",
            "i64.ne",
            "i64.lt_s",
            "i64.lt_u",
            "i64.gt_s",
            "i64.gt_u",
            "i64.le_s",
            "i64.le_u",
            "i64.ge_s",
            "i64.ge_u",
            "f32.eq",
            "f32.ne",
            "f32.lt",
            "f32.gt",
            "f32.le",
            "f32.ge",
            "f64.eq",
            "f64.ne",
            "f64.lt",
            "f64.gt",
            "f64.le",
            "f64.ge"
        };
        names(0x45, comparisons);
        String[] i32 = {
            "i32.clz", "i32.ctz", "i32.popcnt", "i32.add", "i32.sub", "i32.mul",
            "i32.div_s", "i32.div_u", "i32.rem_s", "i32.rem_u", "i32.and", "i32.or",
            "i32.xor", "i32.shl", "i32.shr_s", "i32.shr_u", "i32.rotl", "i32.rotr"
        };
        names(0x67, i32);
        String[] i64 = {
            "i64.clz", "i64.ctz", "i64.popcnt", "i64.add", "i64.sub", "i64.mul",
            "i64.div_s", "i64.div_u", "i64.rem_s", "i64.rem_u", "i64.and", "i64.or",
            "i64.xor", "i64.shl", "i64.shr_s", "i64.shr_u", "i64.rotl", "i64.rotr"
        };
        names(0x79, i64);
        String[] f32 = {
            "f32.abs",
            "f32.neg",
            "f32.ceil",
            "f32.floor",
            "f32.trunc",
            "f32.nearest",
            "f32.sqrt",
            "f32.add",
            "f32.sub",
            "f32.mul",
            "f32.div",
            "f32.min",
            "f32.max",
            "f32.copysign"
        };
        names(0x8b, f32);
        String[] f64 = {
            "f64.abs",
            "f64.neg",
            "f64.ceil",
            "f64.floor",
            "f64.trunc",
            "f64.nearest",
            "f64.sqrt",
            "f64.add",
            "f64.sub",
            "f64.mul",
            "f64.div",
            "f64.min",
            "f64.max",
            "f64.copysign"
        };
        names(0x99, f64);
        String[] conversions = {
            "i32.wrap_i64",
            "i32.trunc_f32_s",
            "i32.trunc_f32_u",
            "i32.trunc_f64_s",
            "i32.trunc_f64_u",
            "i64.extend_i32_s",
            "i64.extend_i32_u",
            "i64.trunc_f32_s",
            "i64.trunc_f32_u",
            "i64.trunc_f64_s",
            "i64.trunc_f64_u",
            "f32.convert_i32_s",
            "f32.convert_i32_u",
            "f32.convert_i64_s",
            "f32.convert_i64_u",
            "f32.demote_f64",
            "f64.convert_i32_s",
            "f64.convert_i32_u",
            "f64.convert_i64_s",
            "f64.convert_i64_u",
            "f64.promote_f32",
            "i32.reinterpret_f32",
            "i64.reinterpret_f64",
            "f32.reinterpret_i32",
            "f64.reinterpret_i64",
            "i32.extend8_s",
            "i32.extend16_s",
            "i64.extend8_s",
            "i64.extend16_s",
            "i64.extend32_s"
        };
        names(0xa7, conversions);
    }

    private OpcodeNames() {}

    static String label(int opcode) {
        if (opcode >= 0 && opcode < STANDARD.length && STANDARD[opcode] != null) {
            return STANDARD[opcode];
        }
        switch (opcode) {
            case 0xfc00:
                return "i32.trunc_sat_f32_s";
            case 0xfc01:
                return "i32.trunc_sat_f32_u";
            case 0xfc02:
                return "i32.trunc_sat_f64_s";
            case 0xfc03:
                return "i32.trunc_sat_f64_u";
            case 0xfc04:
                return "i64.trunc_sat_f32_s";
            case 0xfc05:
                return "i64.trunc_sat_f32_u";
            case 0xfc06:
                return "i64.trunc_sat_f64_s";
            case 0xfc07:
                return "i64.trunc_sat_f64_u";
            case 0xfc08:
                return "memory.init";
            case 0xfc09:
                return "data.drop";
            case 0xfc0a:
                return "memory.copy";
            case 0xfc0b:
                return "memory.fill";
            case WasmModule.W4IR_LOCAL_LOCAL_F32_MUL:
                return "w4ir.local_local_f32_mul";
            case WasmModule.W4IR_LOCAL_F32_CONST_MUL:
                return "w4ir.local_f32_const_mul";
            case WasmModule.W4IR_F32_CONST_MUL_ADD:
                return "w4ir.f32_const_mul_add";
            case WasmModule.W4IR_LOCAL_LOCAL_I32_AND:
                return "w4ir.local_local_i32_and";
            case WasmModule.W4IR_LOCAL_LOCAL_I32_ADD:
                return "w4ir.local_local_i32_add";
            case WasmModule.W4IR_LOCAL_I32_CONST_ADD:
                return "w4ir.local_i32_const_add";
            case WasmModule.W4IR_LOCAL_I32_CONST_AND:
                return "w4ir.local_i32_const_and";
            case WasmModule.W4IR_LOCAL_SET_LOCAL_LOCAL:
                return "w4ir.local_set_local_local";
            case WasmModule.W4IR_LOCAL_I32_CONST_EQ:
                return "w4ir.local_i32_const_eq";
            case WasmModule.W4IR_LOCAL_LOCAL_I32_CONST_AND:
                return "w4ir.local_local_i32_const_and";
            case WasmModule.W4IR_LOCAL_LOCAL_F32_DIV:
                return "w4ir.local_local_f32_div";
            case WasmModule.W4IR_LOCAL_SET_F32_CONST_SET:
                return "w4ir.local_set_f32_const_set";
            case WasmModule.W4IR_LOCAL_LOCAL_F32_LOAD:
                return "w4ir.local_local_f32_load";
            case WasmModule.W4IR_LOCAL_TEE_F32_MUL_ADD:
                return "w4ir.local_tee_f32_mul_add";
            case WasmModule.W4IR_LOCAL_F32_MUL_ADD:
                return "w4ir.local_f32_mul_add";
            case WasmModule.W4IR_LOCAL_LOCAL_F32_MUL_ADD:
                return "w4ir.local_local_f32_mul_add";
            case WasmModule.W4IR_LOCAL_LOCAL:
                return "w4ir.local_local";
            case WasmModule.W4IR_LOCAL_I32_CONST:
                return "w4ir.local_i32_const";
            case WasmModule.W4IR_LOCAL_F32_CONST:
                return "w4ir.local_f32_const";
            case WasmModule.W4IR_F32_MUL_CONST:
                return "w4ir.f32_mul_const";
            case WasmModule.W4IR_F32_MUL_ADD:
                return "w4ir.f32_mul_add";
            case WasmModule.W4IR_I32_ADD_CONST:
                return "w4ir.i32_add_const";
            case WasmModule.W4IR_I32_AND_CONST:
                return "w4ir.i32_and_const";
            case WasmModule.W4IR_LOCAL_SET_GET:
                return "w4ir.local_set_get";
            case WasmModule.W4IR_LOCAL_SET_F32_CONST:
                return "w4ir.local_set_f32_const";
            case WasmModule.W4IR_F32_MUL_LOCAL:
                return "w4ir.f32_mul_local";
            case WasmModule.W4IR_LOCAL_I32_CONST_ADD_SET:
                return "w4ir.local_i32_const_add_set";
            case WasmModule.W4IR_LOCAL_LOCAL_F32_STORE:
                return "w4ir.local_local_f32_store";
            case WasmModule.W4IR_LOCAL_I32_CONST_EQ_BR_IF:
                return "w4ir.local_i32_const_eq_br_if";
            case WasmModule.W4IR_LOCAL_TEE_MUL_ADD_SET_LOCAL_LOCAL:
                return "w4ir.local_tee_mul_add_set_local_local";
            case WasmModule.W4IR_LOCAL_MUL_ADD_SET:
                return "w4ir.local_mul_add_set";
            case WasmModule.W4IR_F32_LOAD_LOCAL_MUL_ADD:
                return "w4ir.f32_load_local_mul_add";
            case WasmModule.W4IR_LOCAL_ADD_SET_BR:
                return "w4ir.local_add_set_br";
            case WasmModule.W4IR_LOCAL_SET_ADD_SET:
                return "w4ir.local_set_add_set";
            case WasmModule.W4IR_LOCAL_SET_LOCAL_LOCAL_F32_LOAD:
                return "w4ir.local_set_local_local_f32_load";
            case WasmModule.W4IR_LOCAL_LOCAL_F32_LOAD_LOCAL:
                return "w4ir.local_local_f32_load_local";
            case WasmModule.W4IR_F32_LOAD_NEG:
                return "w4ir.f32_load_neg";
            case WasmModule.W4IR_F32_LOAD_DIV:
                return "w4ir.f32_load_div";
            case WasmModule.W4IR_F32_DIV_TEE_MUL_ADD_SET_LOCAL_LOCAL:
                return "w4ir.f32_div_tee_mul_add_set_local_local";
            case WasmModule.W4IR_F32_LOAD_LOCAL_MUL_ADD_SET:
                return "w4ir.f32_load_local_mul_add_set";
            case WasmModule.W4IR_BR_IF_LOCAL_I32_CONST:
                return "w4ir.br_if_local_i32_const";
            case WasmModule.W4IR_LOCAL_TRIPLE_F32_STORE:
                return "w4ir.local_triple_f32_store";
            case WasmModule.W4IR_LOCAL4_F32_MUL_ADD_TEE:
                return "w4ir.local4_f32_mul_add_tee";
            case WasmModule.W4IR_F32_LOAD_NEG_INDEX_DIV:
                return "w4ir.f32_load_neg_index_div";
            case WasmModule.W4IR_TRIPLE_F32_STORE_LOCAL_LOAD_LOCAL:
                return "w4ir.triple_f32_store_local_load_local";
            case WasmModule.W4IR_LOCAL_TEE_MUL_ADD_SET_LOAD_MUL_ADD:
                return "w4ir.local_tee_mul_add_set_load_mul_add";
            case WasmModule.W4IR_LOCAL_SET_DUAL_ADD_SET:
                return "w4ir.local_set_dual_add_set";
            case WasmModule.W4IR_COUNTED_F32_TRACE:
                return "w4ir.counted_f32_trace";
            case WasmModule.W4IR_F32_FLOOR_INTRINSIC:
                return "w4ir.f32_floor_intrinsic";
            case WasmModule.W4IR_F32_SIN_INTRINSIC:
                return "w4ir.f32_sin_intrinsic";
            case WasmModule.W4IR_I32_LOAD_LOCAL_TEE:
                return "w4ir.i32_load_local_tee";
            default:
                return "opcode_0x" + hex4(opcode);
        }
    }

    static String breakReason(int reason) {
        switch (reason) {
            case WasmInterpreter.COMPACT_BREAK_END:
                return "end-of-function";
            case WasmInterpreter.COMPACT_BREAK_BRANCH_TARGET:
                return "branch-target";
            case WasmInterpreter.COMPACT_BREAK_INELIGIBLE_OPCODE:
                return "ineligible-opcode";
            case WasmInterpreter.COMPACT_BREAK_INVALID_SPAN:
                return "invalid-span";
            case WasmInterpreter.COMPACT_BREAK_DISPATCH_LIMIT:
                return "dispatch-limit";
            default:
                return "unknown-" + reason;
        }
    }

    static String hex4(int value) {
        String text = Integer.toHexString(value & 0xffff);
        StringBuffer result = new StringBuffer(4);
        int index;
        for (index = text.length(); index < 4; index++) {
            result.append('0');
        }
        result.append(text);
        return result.toString();
    }

    private static void names(int firstOpcode, String[] values) {
        int index;
        for (index = 0; index < values.length; index++) {
            name(firstOpcode + index, values[index]);
        }
    }

    private static void name(int opcode, String value) {
        STANDARD[opcode] = value;
    }
}
