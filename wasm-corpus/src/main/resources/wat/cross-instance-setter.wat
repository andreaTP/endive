;; Writes its function, which returns 42, into the holder's table with table.set, grow and fill.
(module
  (import "holder" "tab" (table 2 funcref))
  (global $g i32 (i32.const 42))

  (func $answer (result i32) global.get $g)
  (elem declare func $answer)

  (func (export "set") (param i32)
    (table.set (local.get 0) (ref.func $answer)))

  (func (export "grow") (result i32)
    (table.grow (ref.func $answer) (i32.const 1)))

  (func (export "fill")
    (table.fill (i32.const 0) (ref.func $answer) (i32.const 2)))
)
