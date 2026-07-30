(module
  (memory (export "memory") 1)
  (global $first (mut f32) (f32.const 0))
  (global $second (mut f32) (f32.const 0))
  (global $third (mut f32) (f32.const 0))

  ;; Function zero deliberately has a non-f32 signature. The RMS corruption
  ;; smoke assigns it a cached f32 intrinsic and requires reject-and-rebuild.
  (func $identity (param i32) (result i32)
    local.get 0)

  (func (export "update")
    (local $index i32)
    (local $first-local f32)
    (local $second-local f32)

    ;; Cross the compact activation threshold before the focused f32 pairs.
    (loop $warm
      local.get $index
      i32.const 1
      i32.add
      local.tee $index
      i32.const 70000
      i32.lt_u
      br_if $warm)

    ;; local.get + f32.const
    local.get $first-local
    f32.const -1
    global.set $first
    drop

    ;; f32.const + local.set
    f32.const -2
    local.set $first-local
    local.get $first-local
    global.set $second

    ;; local.set + the fused f32.const/local.set pair
    local.get $first-local
    local.set $first-local
    f32.const -4
    local.set $second-local
    local.get $second-local
    global.set $third))
