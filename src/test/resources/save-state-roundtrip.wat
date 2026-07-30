(module
  (import "env" "memory" (memory 0 65536))
  (table 2 funcref)
  (global $counter (mut i64) (i64.const 7))
  (data $payload "STATE")

  (func $target)
  (elem (i32.const 0) $target)

  (func (export "update")))
