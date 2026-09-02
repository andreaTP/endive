;; Mutates every kind of import it was given, so the caller can check that its
;; own objects observe the change. A backend that copies an import instead of
;; sharing it comes apart here: wasm sees the new value and the host does not.
(module
  (import "env" "memory" (memory 1))
  (import "env" "table" (table 2 funcref))
  (import "env" "counter" (global $counter (mut i32)))

  (elem declare func $answer)

  (func $answer (result i32)
    (i32.const 42))

  (func (export "run")
    (global.set $counter (i32.add (global.get $counter) (i32.const 1)))
    (i32.store (i32.const 0) (i32.const 23130))
    (table.set (i32.const 0) (ref.func $answer)))

  ;; reads what the host left at addr and writes twice that to addr+4, so a
  ;; round trip through the imported memory is observable from both ends
  (func (export "doubleAt") (param $addr i32)
    (i32.store
      (i32.add (local.get $addr) (i32.const 4))
      (i32.mul (i32.load (local.get $addr)) (i32.const 2))))
)
